package com.tcc.servicedesk.ticket.attachment;

import com.tcc.servicedesk.security.UserPrincipal;
import com.tcc.servicedesk.storage.StorageService;
import com.tcc.servicedesk.ticket.Ticket;
import com.tcc.servicedesk.ticket.TicketRepository;
import com.tcc.servicedesk.ticket.attachment.dto.AttachmentResponse;
import com.tcc.servicedesk.ticket.audit.AuditEntry;
import com.tcc.servicedesk.ticket.audit.AuditEntryRepository;
import com.tcc.servicedesk.user.User;
import com.tcc.servicedesk.user.UserRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttachmentService {

    private static final long MAX_SIZE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_ATTACHMENTS_PER_TICKET = 5;
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "png", "jpg", "jpeg", "txt", "log", "zip");

    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditEntryRepository auditEntryRepository;
    private final StorageService storageService;
    private final FileContentValidator fileContentValidator;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository,
            AuditEntryRepository auditEntryRepository,
            StorageService storageService,
            FileContentValidator fileContentValidator) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.auditEntryRepository = auditEntryRepository;
        this.storageService = storageService;
        this.fileContentValidator = fileContentValidator;
    }

    @Transactional
    public AttachmentResponse uploadAttachment(
            Long ticketId, UserPrincipal caller, MultipartFile file) {
        Ticket ticket =
                ticketRepository
                        .findById(ticketId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Ticket not found"));

        User uploader =
                userRepository
                        .findById(caller.getId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not found"));

        checkUploadPermission(ticket, caller);

        if (attachmentRepository.countByTicketIdAndDeletedFalse(ticketId)
                >= MAX_ATTACHMENTS_PER_TICKET) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This ticket already has the maximum of "
                            + MAX_ATTACHMENTS_PER_TICKET
                            + " attachments");
        }

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File exceeds 10 MB limit");
        }

        String originalFilename =
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        String extension = extractExtension(originalFilename);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "File type not supported: " + extension);
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Could not read file");
        }

        FileContentValidator.DetectedType detected = fileContentValidator.detect(content);

        if (detected == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "File content does not match any supported type");
        }

        boolean categoryMatches =
                switch (extension) {
                    case "jpg", "jpeg" -> detected.category().equals("jpg");
                    case "txt", "log" -> detected.category().equals("text");
                    default -> detected.category().equals(extension);
                };

        if (!categoryMatches) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File content does not match its extension (." + extension + ")");
        }

        String storageKey =
                "tickets/" + ticketId + "/" + UUID.randomUUID() + "-" + sanitize(originalFilename);

        storageService.upload(storageKey, content, detected.contentType());

        Attachment attachment =
                Attachment.builder()
                        .ticket(ticket)
                        .filename(originalFilename)
                        .contentType(detected.contentType())
                        .sizeBytes(content.length)
                        .storageKey(storageKey)
                        .uploadedBy(uploader)
                        .build();

        attachmentRepository.save(attachment);

        auditEntryRepository.save(
                AuditEntry.builder()
                        .ticket(ticket)
                        .actor(uploader)
                        .field("attachment")
                        .oldValue(null)
                        .newValue(originalFilename)
                        .createdAt(Instant.now())
                        .build());

        return toResponse(attachment);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listAttachments(Long ticketId, UserPrincipal caller) {
        Ticket ticket =
                ticketRepository
                        .findById(ticketId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Ticket not found"));

        checkViewPermission(ticket, caller);

        return attachmentRepository.findByTicketIdAndDeletedFalse(ticketId).stream()
                .map(this::toResponse)
                .toList();
    }

    public record DownloadedFile(byte[] content, String filename, String contentType) {}

    @Transactional(readOnly = true)
    public DownloadedFile downloadAttachment(Long attachmentId, UserPrincipal caller) {
        Attachment attachment =
                attachmentRepository
                        .findById(attachmentId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Attachment not found"));

        checkViewPermission(attachment.getTicket(), caller);

        byte[] content = storageService.download(attachment.getStorageKey());

        return new DownloadedFile(content, attachment.getFilename(), attachment.getContentType());
    }

    @Transactional
    public void deleteAttachment(Long attachmentId, UserPrincipal caller) {
        Attachment attachment =
                attachmentRepository
                        .findById(attachmentId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Attachment not found"));

        boolean isUploader = attachment.getUploadedBy().getId().equals(caller.getId());
        boolean isAdmin =
                caller.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isUploader && !isAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the uploader or an admin can delete this attachment");
        }

        attachment.setDeleted(true);

        User actor =
                userRepository
                        .findById(caller.getId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "User not found"));

        auditEntryRepository.save(
                AuditEntry.builder()
                        .ticket(attachment.getTicket())
                        .actor(actor)
                        .field("attachment")
                        .oldValue(attachment.getFilename())
                        .newValue(null)
                        .createdAt(Instant.now())
                        .build());
    }

    private void checkUploadPermission(Ticket ticket, UserPrincipal caller) {
        if (isRequesterOnly(caller) && !ticket.getRequester().getId().equals(caller.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You may only upload attachments to your own tickets");
        }
    }

    private void checkViewPermission(Ticket ticket, UserPrincipal caller) {
        if (isRequesterOnly(caller) && !ticket.getRequester().getId().equals(caller.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You may only access attachments on your own tickets");
        }
    }

    private boolean isRequesterOnly(UserPrincipal caller) {
        return caller.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .allMatch(role -> role.equals("ROLE_REQUESTER"));
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    private String sanitize(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private AttachmentResponse toResponse(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getTicket().getId(),
                attachment.getFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getUploadedBy().getId(),
                attachment.getUploadedBy().getFullName(),
                attachment.getUploadedAt());
    }
}
