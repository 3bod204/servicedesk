package com.tcc.servicedesk.ticket.comment.dto;

import java.time.Instant;

public record CommentResponse(
        Long id,
        Long ticketId,
        Long parentId,
        Long authorId,
        String authorName,
        String body,
        boolean internal,
        Instant createdAt) {}
