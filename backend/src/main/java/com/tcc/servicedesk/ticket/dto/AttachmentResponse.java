package com.tcc.servicedesk.ticket.dto;

import java.time.Instant;

public record AttachmentResponse(
        Long id,
        Long ticketId,
        String filename,
        String contentType,
        long sizeBytes,
        Long uploadedById,
        String uploadedByName,
        Instant uploadedAt
) {}