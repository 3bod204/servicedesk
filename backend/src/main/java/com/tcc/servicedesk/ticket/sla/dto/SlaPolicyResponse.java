package com.tcc.servicedesk.ticket.sla.dto;

import com.tcc.servicedesk.ticket.Priority;

public record SlaPolicyResponse(
        Long id, Priority priority, int firstResponseMinutes, int resolutionMinutes) {}
