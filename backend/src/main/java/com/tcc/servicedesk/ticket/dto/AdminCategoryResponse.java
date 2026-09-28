package com.tcc.servicedesk.ticket.dto;

public record AdminCategoryResponse(
        Long id, String name, Long queueId, String queueName, boolean active) {}
