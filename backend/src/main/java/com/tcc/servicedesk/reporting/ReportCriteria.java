package com.tcc.servicedesk.reporting;

import java.time.Instant;

public record ReportCriteria(Instant dateFrom, Instant dateTo, Long queueId) {}
