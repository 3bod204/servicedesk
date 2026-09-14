package com.tcc.servicedesk.ticket.dto;

import com.tcc.servicedesk.ticket.Priority;
import com.tcc.servicedesk.ticket.TicketStatus;

import java.time.Instant;

public record TicketSearchCriteria(
    TicketStatus status,
    Priority priority,
    Long queueId,
    Long assigneeId,
    Long categoryId,
    Instant createdFrom,
    Instant createdTo,
    String search
) {}
