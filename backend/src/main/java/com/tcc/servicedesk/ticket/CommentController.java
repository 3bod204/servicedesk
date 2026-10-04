package com.tcc.servicedesk.ticket;

import com.tcc.servicedesk.security.UserPrincipal;
import com.tcc.servicedesk.ticket.dto.CommentResponse;
import com.tcc.servicedesk.ticket.dto.CreateCommentRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tickets/{ticketId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse createComment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentRequest request) {
        return commentService.createComment(ticketId, principal, request);
    }

    @GetMapping
    public List<CommentResponse> listComments(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long ticketId) {
        return commentService.listComments(ticketId, principal);
    }
}
