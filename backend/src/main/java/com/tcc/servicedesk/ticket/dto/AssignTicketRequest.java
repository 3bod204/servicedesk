package com.tcc.servicedesk.ticket.dto;

import jakarta.validation.constraints.NotNull;

public record AssignTicketRequest(
    @NotNull(message = "Assignee is required")
    Long assigneeId
) {}
