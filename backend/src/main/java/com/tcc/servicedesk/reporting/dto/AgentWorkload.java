package com.tcc.servicedesk.reporting.dto;

public record AgentWorkload(Long agentId, String agentName, Long openTicketCount) {}
