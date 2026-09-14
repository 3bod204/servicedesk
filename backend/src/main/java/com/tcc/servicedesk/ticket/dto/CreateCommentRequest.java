package com.tcc.servicedesk.ticket.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCommentRequest(

        @NotBlank(message = "Comment body is required")
        String body,

        boolean internal,

        Long parentId
) {}