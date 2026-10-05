package com.tcc.servicedesk.ticket.queue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateCategoryRequest(@NotBlank String name, @NotNull Long queueId, boolean active) {}
