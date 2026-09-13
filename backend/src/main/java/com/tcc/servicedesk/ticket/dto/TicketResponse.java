package com.tcc.servicedesk.ticket.dto;

import java.time.Instant;

import com.tcc.servicedesk.ticket.Priority;
import com.tcc.servicedesk.ticket.TicketStatus;

public record TicketResponse(
    Long id,
    String reference,
    String title,
    String description,
    TicketStatus ticketStatus,
    Priority priority,
    String categoryName,
    String queueName,
    Long requesterId,
    String requesterName,
    Long assigneeId,
    String assigneeName,
    Instant createdAt,
    Instant slaDueAt
) {}
