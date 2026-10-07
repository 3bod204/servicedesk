package com.tcc.servicedesk.reporting.dto;

public record SlaComplianceMetric(
        long totalResolved, long compliant, Double compliancePercentage) {}
