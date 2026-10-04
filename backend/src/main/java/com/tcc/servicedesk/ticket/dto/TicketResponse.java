package com.tcc.servicedesk.ticket.dto;

import com.tcc.servicedesk.ticket.Priority;
import com.tcc.servicedesk.ticket.TicketStatus;
import java.time.Instant;

public record TicketResponse(
        Long id,
        String reference,
        String title,
        String description,
        TicketStatus status,
        Priority priority,
        String categoryName,
        Long queueId,
        String queueName,
        Long requesterId,
        String requesterName,
        Long assigneeId,
        String assigneeName,
        Instant createdAt,
        Instant slaDueAt,
        Instant resolvedAt,
        Instant closedAt) {}
