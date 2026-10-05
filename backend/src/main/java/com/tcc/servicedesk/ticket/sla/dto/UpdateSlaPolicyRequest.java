package com.tcc.servicedesk.ticket.sla.dto;

import jakarta.validation.constraints.Min;

public record UpdateSlaPolicyRequest(
        @Min(1) int firstResponseMinutes, @Min(1) int resolutionMinutes) {}
