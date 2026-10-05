package com.tcc.servicedesk.ticket.attachment;

import com.tcc.servicedesk.security.UserPrincipal;
import com.tcc.servicedesk.ticket.attachment.dto.AttachmentResponse;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/tickets/{ticketId}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long ticketId,
            @RequestParam("file") MultipartFile file) {
        return attachmentService.uploadAttachment(ticketId, principal, file);
    }

    @GetMapping("/tickets/{ticketId}/attachments")
    public List<AttachmentResponse> list(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long ticketId) {
        return attachmentService.listAttachments(ticketId, principal);
    }

    @DeleteMapping("/attachments/{id}")
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        attachmentService.deleteAttachment(id, principal);
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        AttachmentService.DownloadedFile file = attachmentService.downloadAttachment(id, principal);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }
}
