package com.tcc.servicedesk.reporting;

public record AgentWorkload(Long agentId, String agentName, Long openTicketCount) {}
