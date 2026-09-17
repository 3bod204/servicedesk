package com.tcc.servicedesk.reporting;

public record SlaComplianceMetric(
        long totalResolved, long compliant, Double compliancePercentage) {}
