package com.tcc.servicedesk.ticket.queue.dto;

public record AdminCategoryResponse(
        Long id, String name, Long queueId, String queueName, boolean active) {}
