package com.tcc.servicedesk.ticket.dto;

import com.tcc.servicedesk.ticket.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTicketRequest(
        @NotBlank(message = "Title is required") String title,
        @NotBlank(message = "Description is required") String description,
        @NotNull(message = "Category is required") Long categoryId,
        @NotNull(message = "Priority is required") Priority priority) {}
