package com.tcc.servicedesk.reporting;

import com.tcc.servicedesk.ticket.Priority;
import com.tcc.servicedesk.ticket.Ticket;
import com.tcc.servicedesk.ticket.TicketRepository;
import com.tcc.servicedesk.ticket.TicketStatus;
import com.tcc.servicedesk.user.User;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ReportingService {

    private final TicketRepository ticketRepository;

    public ReportingService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public List<StatusCount> getOpenTicketsByStatus(ReportCriteria criteria) {
        List<Ticket> tickets = fetch(criteria);

        return List.of(TicketStatus.values()).stream()
                .map(
                        status ->
                                new StatusCount(
                                        status.name(),
                                        tickets.stream()
                                                .filter(t -> t.getStatus() == status)
                                                .count()))
                .toList();
    }

    public List<PriorityCount> getTicketsByPriority(ReportCriteria criteria) {
        List<Ticket> tickets = fetch(criteria);

        return List.of(Priority.values()).stream()
                .map(
                        priority ->
                                new PriorityCount(
                                        priority.name(),
                                        tickets.stream()
                                                .filter(t -> t.getPriority() == priority)
                                                .count()))
                .toList();
    }

    public AverageTimeMetric getAverageFirstResponseTime(ReportCriteria criteria) {
        List<Ticket> tickets =
                fetch(criteria).stream().filter(t -> t.getFirstResponseAt() != null).toList();

        if (tickets.isEmpty()) {
            return new AverageTimeMetric("average_first_response_minutes", null);
        }

        double totalMinutes =
                tickets.stream()
                        .mapToLong(
                                t ->
                                        ChronoUnit.MINUTES.between(
                                                t.getCreatedAt(), t.getFirstResponseAt()))
                        .sum();

        return new AverageTimeMetric(
                "average_first_response_minutes", totalMinutes / tickets.size());
    }

    public AverageTimeMetric getAverageResolutionTime(ReportCriteria criteria) {
        List<Ticket> tickets =
                fetch(criteria).stream().filter(t -> t.getResolvedAt() != null).toList();

        if (tickets.isEmpty()) {
            return new AverageTimeMetric("average_resolution_minutes", null);
        }

        double totalMinutes =
                tickets.stream()
                        .mapToLong(
                                t ->
                                        ChronoUnit.MINUTES.between(
                                                        t.getCreatedAt(), t.getResolvedAt())
                                                - t.getPendingMinutesTotal())
                        .sum();

        return new AverageTimeMetric("average_resolution_minutes", totalMinutes / tickets.size());
    }

    public SlaComplianceMetric getSlaCompliance(ReportCriteria criteria) {
        List<Ticket> resolvedTickets =
                fetch(criteria).stream().filter(t -> t.getResolvedAt() != null).toList();

        if (resolvedTickets.isEmpty()) {
            return new SlaComplianceMetric(0, 0, null);
        }

        long compliant = resolvedTickets.stream().filter(t -> !t.isResolutionBreached()).count();

        double percentage = (compliant * 100.0) / resolvedTickets.size();

        return new SlaComplianceMetric(resolvedTickets.size(), compliant, percentage);
    }

    public List<AgentWorkload> getAgentWorkload(ReportCriteria criteria) {
        List<TicketStatus> closedStatuses = List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED);

        List<Ticket> openAssignedTickets =
                fetch(criteria).stream()
                        .filter(t -> t.getAssignee() != null)
                        .filter(t -> !closedStatuses.contains(t.getStatus()))
                        .toList();

        Map<User, Long> countsByAgent =
                openAssignedTickets.stream()
                        .collect(Collectors.groupingBy(Ticket::getAssignee, Collectors.counting()));

        return countsByAgent.entrySet().stream()
                .map(
                        entry ->
                                new AgentWorkload(
                                        entry.getKey().getId(),
                                        entry.getKey().getFullName(),
                                        entry.getValue()))
                .toList();
    }

    private List<Ticket> fetch(ReportCriteria criteria) {
        return ticketRepository.findAllByDeletedFalse().stream()
                .filter(
                        t ->
                                criteria.dateFrom() == null
                                        || !t.getCreatedAt().isBefore(criteria.dateFrom()))
                .filter(
                        t ->
                                criteria.dateTo() == null
                                        || !t.getCreatedAt().isAfter(criteria.dateTo()))
                .filter(
                        t ->
                                criteria.queueId() == null
                                        || t.getQueue().getId().equals(criteria.queueId()))
                .toList();
    }
}
