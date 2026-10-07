package com.tcc.servicedesk.ticket.comment;

import com.tcc.servicedesk.notification.NotificationService;
import com.tcc.servicedesk.security.UserPrincipal;
import com.tcc.servicedesk.ticket.Ticket;
import com.tcc.servicedesk.ticket.TicketRepository;
import com.tcc.servicedesk.ticket.audit.AuditEntry;
import com.tcc.servicedesk.ticket.audit.AuditEntryRepository;
import com.tcc.servicedesk.ticket.comment.dto.CommentResponse;
import com.tcc.servicedesk.ticket.comment.dto.CreateCommentRequest;
import com.tcc.servicedesk.user.User;
import com.tcc.servicedesk.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AuditEntryRepository auditEntryRepository;
    private final NotificationService notificationService;

    public CommentService(
            CommentRepository commentRepository,
            TicketRepository ticketRepository,
            UserRepository userRepository,
            AuditEntryRepository auditEntryRepository,
            NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.auditEntryRepository = auditEntryRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public CommentResponse createComment(
            Long ticketId, UserPrincipal caller, CreateCommentRequest request) {
        Ticket ticket =
                ticketRepository
                        .findById(ticketId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Ticket not found"));

        User author =
                userRepository
                        .findById(caller.getId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Author not found"));

        boolean isRequesterOnly = isRequesterOnly(caller);

        if (isRequesterOnly) {
            if (!ticket.getRequester().getId().equals(caller.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "You may only comment on your own tickets");
            }
            if (request.internal()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Requesters cannot create internal comments");
            }
        }

        Comment parent = null;
        if (request.parentId() != null) {
            parent =
                    commentRepository
                            .findById(request.parentId())
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "Parent comment not found"));

            if (!parent.getTicket().getId().equals(ticketId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Parent comment belongs to a different ticket");
            }
        }

        Comment comment =
                Comment.builder()
                        .ticket(ticket)
                        .parent(parent)
                        .author(author)
                        .body(request.body())
                        .internal(request.internal())
                        .build();

        commentRepository.save(comment);
        if (!comment.isInternal()) {
            notificationService.notifyCommentAdded(ticket, author.getFullName());
        }

        auditEntryRepository.save(
                AuditEntry.builder()
                        .ticket(ticket)
                        .actor(author)
                        .field("comment")
                        .oldValue(null)
                        .newValue(
                                request.internal()
                                        ? "internal comment added"
                                        : "public comment added")
                        .createdAt(Instant.now())
                        .build());

        return toResponse(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(Long ticketId, UserPrincipal caller) {

        Ticket ticket =
                ticketRepository
                        .findById(ticketId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Ticket not found"));

        boolean isRequesterOnly = isRequesterOnly(caller);

        if (isRequesterOnly && !ticket.getRequester().getId().equals(caller.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You may only view comments on your own tickets");
        }

        List<Comment> comments =
                isRequesterOnly
                        ? commentRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(
                                ticketId)
                        : commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

        return comments.stream().map(this::toResponse).toList();
    }

    private boolean isRequesterOnly(UserPrincipal caller) {
        return caller.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .allMatch(role -> role.equals("ROLE_REQUESTER"));
    }

    private CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getTicket().getId(),
                comment.getParent() != null ? comment.getParent().getId() : null,
                comment.getAuthor().getId(),
                comment.getAuthor().getFullName(),
                comment.getBody(),
                comment.isInternal(),
                comment.getCreatedAt());
    }
}
