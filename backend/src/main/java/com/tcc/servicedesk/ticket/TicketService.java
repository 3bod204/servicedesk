package com.tcc.servicedesk.ticket;

import com.tcc.servicedesk.ticket.dto.CreateTicketRequest;
import com.tcc.servicedesk.ticket.dto.TicketResponse;
import com.tcc.servicedesk.ticket.dto.UpdateStatusRequest;
import com.tcc.servicedesk.user.User;
import com.tcc.servicedesk.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final TicketReferenceGenerator referenceGenerator;
    private final SlaPolicyRepository slaPolicyRepository;

    public TicketService(
            TicketRepository ticketRepository,
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            TicketReferenceGenerator referenceGenerator,
            SlaPolicyRepository slaPolicyRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.referenceGenerator = referenceGenerator;
        this.slaPolicyRepository = slaPolicyRepository;
    }

    @Transactional
    public TicketResponse createTicket(Long requesterId, CreateTicketRequest request) {

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Requester not found"));

        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Category not found"));

        SlaPolicy slaPolicy = slaPolicyRepository.findByPriority(request.priority())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "No SLA policy configured for priority: " + request.priority()));

        Instant now = Instant.now();

        Ticket ticket = Ticket.builder()
                .reference(referenceGenerator.generate())
                .title(request.title())
                .description(request.description())
                .status(TicketStatus.NEW)
                .priority(request.priority())
                .category(category)
                .queue(category.getQueue())
                .requester(requester)
                .createdAt(now)
                .updatedAt(now)
                .firstResponseDueAt(now.plus(slaPolicy.getFirstResponseMinutes(), ChronoUnit.MINUTES))
                .slaDueAt(now.plus(slaPolicy.getResolutionMinutes(), ChronoUnit.MINUTES))
                .build();

        ticketRepository.save(ticket);

        return toResponse(ticket);
    }

    private TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getReference(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getCategory().getName(),
                ticket.getQueue().getName(),
                ticket.getRequester().getId(),
                ticket.getRequester().getFullName(),
                ticket.getAssignee() != null ? ticket.getAssignee().getId() : null,
                ticket.getAssignee() != null ? ticket.getAssignee().getFullName() : null,
                ticket.getCreatedAt(),
                ticket.getSlaDueAt()
        );
    }

    public TicketResponse changeStatus(Long ticketId, UpdateStatusRequest request) {

    Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Ticket not found"));

    TicketStatus oldStatus = ticket.getStatus();
    TicketStatus newStatus = request.status();

    if (!TicketStatusTransitions.isValid(oldStatus, newStatus)) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                String.format("Cannot transition from %s to %s", oldStatus, newStatus));
    }

    if (newStatus == TicketStatus.REOPENED) {
        Instant reopenDeadline = (oldStatus == TicketStatus.CLOSED
                ? ticket.getClosedAt()
                : ticket.getResolvedAt())
                .plus(14, ChronoUnit.DAYS);

        if (Instant.now().isAfter(reopenDeadline)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ticket can no longer be reopened; the 14-day window has passed");
        }
    }

    Instant now = Instant.now();

    if (oldStatus == TicketStatus.NEW && ticket.getFirstResponseAt() == null) {
        ticket.setFirstResponseAt(now);
    }

    if (oldStatus == TicketStatus.PENDING_REQUESTER && ticket.getPendingSince() != null) {
        long minutesPending = ChronoUnit.MINUTES.between(ticket.getPendingSince(), now);
        ticket.setPendingMinutesTotal(ticket.getPendingMinutesTotal() + (int) minutesPending);
        ticket.setPendingSince(null);
    }

    if (newStatus == TicketStatus.PENDING_REQUESTER) {
        ticket.setPendingSince(now);
    }

    if (newStatus == TicketStatus.RESOLVED) {
        ticket.setResolvedAt(now);
    }

    if (newStatus == TicketStatus.CLOSED) {
        ticket.setClosedAt(now);
    }

    ticket.setStatus(newStatus);
    ticket.setUpdatedAt(now);

    return toResponse(ticket);
}
}