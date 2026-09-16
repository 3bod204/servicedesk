package com.tcc.servicedesk.ticket.dto;

import java.time.Instant;

public record AuditEntryResponse(
        Long id,
        String field,
        String oldValue,
        String newValue,
        Long actorId,
        String actorName,
        Instant createdAt
) {}