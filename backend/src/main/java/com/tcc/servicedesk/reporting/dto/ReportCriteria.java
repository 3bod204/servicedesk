package com.tcc.servicedesk.reporting.dto;

import java.time.Instant;

public record ReportCriteria(Instant dateFrom, Instant dateTo, Long queueId) {}
