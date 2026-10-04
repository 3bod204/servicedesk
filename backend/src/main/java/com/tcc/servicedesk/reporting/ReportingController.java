package com.tcc.servicedesk.reporting;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/tickets-by-status")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public List<StatusCount> getTicketsByStatus(@ModelAttribute ReportCriteria criteria) {
        return reportingService.getOpenTicketsByStatus(criteria);
    }

    @GetMapping("/tickets-by-priority")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public List<PriorityCount> getTicketsByPriority(@ModelAttribute ReportCriteria criteria) {
        return reportingService.getTicketsByPriority(criteria);
    }

    @GetMapping("/avg-first-response-time")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public AverageTimeMetric getAverageFirstResponseTime(@ModelAttribute ReportCriteria criteria) {
        return reportingService.getAverageFirstResponseTime(criteria);
    }

    @GetMapping("/avg-resolution-time")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public AverageTimeMetric getAverageResolutionTime(@ModelAttribute ReportCriteria criteria) {
        return reportingService.getAverageResolutionTime(criteria);
    }

    @GetMapping("/sla-compliance")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public SlaComplianceMetric getSlaCompliance(@ModelAttribute ReportCriteria criteria) {
        return reportingService.getSlaCompliance(criteria);
    }

    @GetMapping("/agent-workload")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public List<AgentWorkload> getAgentWorkload(@ModelAttribute ReportCriteria criteria) {
        return reportingService.getAgentWorkload(criteria);
    }
}
