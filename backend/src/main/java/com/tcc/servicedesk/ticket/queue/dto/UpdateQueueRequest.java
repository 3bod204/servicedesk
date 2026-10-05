package com.tcc.servicedesk.ticket.queue.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateQueueRequest(@NotBlank String name, String description, boolean active) {}
