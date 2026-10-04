package com.tcc.servicedesk.ticket.dto;

import com.tcc.servicedesk.ticket.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull(message = "Status is required") TicketStatus status) {}
