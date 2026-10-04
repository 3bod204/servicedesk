package com.tcc.servicedesk.ticket;

import com.tcc.servicedesk.notification.NotificationService;
import com.tcc.servicedesk.security.UserPrincipal;
import com.tcc.servicedesk.ticket.dto.AdminCategoryResponse;
import com.tcc.servicedesk.ticket.dto.AdminQueueResponse;
import com.tcc.servicedesk.ticket.dto.AssignTicketRequest;
import com.tcc.servicedesk.ticket.dto.AuditEntryResponse;
import com.tcc.servicedesk.ticket.dto.CategoryResponse;
import com.tcc.servicedesk.ticket.dto.CreateTicketRequest;
import com.tcc.servicedesk.ticket.dto.QueueResponse;
import com.tcc.servicedesk.ticket.dto.SlaPolicyResponse;
import com.tcc.servicedesk.ticket.dto.TicketResponse;
import com.tcc.servicedesk.ticket.dto.TicketSearchCriteria;
import com.tcc.servicedesk.ticket.dto.UpdateCategoryRequest;
import com.tcc.servicedesk.ticket.dto.UpdateQueueRequest;
import com.tcc.servicedesk.ticket.dto.UpdateSlaPolicyRequest;
import com.tcc.servicedesk.ticket.dto.UpdateStatusRequest;
import com.tcc.servicedesk.user.User;
import com.tcc.servicedesk.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CategoryRepository categoryRepository;
    private final QueueRepository queueRepository;
    private final UserRepository userRepository;
    private final TicketReferenceGenerator referenceGenerator;
    private final SlaPolicyRepository slaPolicyRepository;
    private final AuditEntryRepository auditEntryRepository;
    private final NotificationService notificationService;

    public TicketService(
            TicketRepository ticketRepository,
            CategoryRepository categoryRepository,
            QueueRepository queueRepository,
            UserRepository userRepository,
            TicketReferenceGenerator referenceGenerator,
            SlaPolicyRepository slaPolicyRepository,
            AuditEntryRepository auditEntryRepository,
            NotificationService notificationService) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.queueRepository = queueRepository;
        this.userRepository = userRepository;
        this.referenceGenerator = referenceGenerator;
        this.slaPolicyRepository = slaPolicyRepository;
        this.auditEntryRepository = auditEntryRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public TicketResponse createTicket(Long requesterId, CreateTicketRequest request) {

        User requester =
                userRepository
                        .findById(requesterId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Requester not found"));

        Category category =
                categoryRepository
                        .findById(request.categoryId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST, "Category not found"));

        SlaPolicy slaPolicy =
                slaPolicyRepository
                        .findByPriority(request.priority())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                "No SLA policy configured for priority: "
                                                        + request.priority()));

        Instant now = Instant.now();

        Ticket ticket =
                Ticket.builder()
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
                        .firstResponseDueAt(
                                now.plus(slaPolicy.getFirstResponseMinutes(), ChronoUnit.MINUTES))
                        .slaDueAt(now.plus(slaPolicy.getResolutionMinutes(), ChronoUnit.MINUTES))
                        .build();

        ticketRepository.save(ticket);

        notificationService.notifyTicketCreated(ticket);

        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public TicketResponse getById(Long ticketId, UserPrincipal caller) {
        Ticket ticket =
                ticketRepository
                        .findById(ticketId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Ticket not found"));

        boolean isRequesterOnly =
                caller.getAuthorities().stream()
                        .map(a -> a.getAuthority())
                        .allMatch(role -> role.equals("ROLE_REQUESTER"));

        if (isRequesterOnly && !ticket.getRequester().getId().equals(caller.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "You may only view your own ticket");
        }
        return toResponse(ticket);
    }

    @Transactional
    public TicketResponse changeStatus(
            Long ticketId, Long actingUserId, UpdateStatusRequest request) {

        Ticket ticket =
                ticketRepository
                        .findById(ticketId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Ticket not found"));

        User actor =
                userRepository
                        .findById(actingUserId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Acting user not found"));

        TicketStatus oldStatus = ticket.getStatus();
        TicketStatus newStatus = request.status();

        if (!TicketStatusTransitions.isValid(oldStatus, newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    String.format("Cannot transition from %s to %s", oldStatus, newStatus));
        }

        if (newStatus == TicketStatus.REOPENED) {
            Instant reopenDeadline =
                    (oldStatus == TicketStatus.CLOSED
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
            notificationService.notifyTicketResolved(ticket);
        }

        if (newStatus == TicketStatus.CLOSED) {
            ticket.setClosedAt(now);
        }

        ticket.setStatus(newStatus);
        ticket.setUpdatedAt(now);

        auditEntryRepository.save(
                AuditEntry.builder()
                        .ticket(ticket)
                        .actor(actor)
                        .field("status")
                        .oldValue(oldStatus.name())
                        .newValue(newStatus.name())
                        .createdAt(now)
                        .build());

        return toResponse(ticket);
    }

    @Transactional
    public AdminQueueResponse createQueue(UpdateQueueRequest request) {
        if (queueRepository.findByName(request.name()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A queue with that name already exists");
        }
        Queue queue =
                Queue.builder()
                        .name(request.name())
                        .description(request.description())
                        .active(true)
                        .build();
        queueRepository.save(queue);
        return toAdminQueue(queue);
    }

    @Transactional
    public AdminQueueResponse updateQueue(Long id, UpdateQueueRequest request) {
        Queue queue =
                queueRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Queue not found"));
        queue.setName(request.name());
        queue.setDescription(request.description());
        queue.setActive(request.active());
        return toAdminQueue(queue);
    }

    @Transactional
    public List<AdminQueueResponse> listAllQueues() {
        return queueRepository.findAll().stream().map(this::toAdminQueue).toList();
    }

    @Transactional
    public AdminCategoryResponse createCategory(UpdateCategoryRequest request) {
        Queue queue =
                queueRepository
                        .findById(request.queueId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST, "Queue not found"));
        Category category =
                Category.builder().name(request.name()).queue(queue).active(true).build();
        categoryRepository.save(category);
        return toAdminCategory(category);
    }

    @Transactional
    public AdminCategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category =
                categoryRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Category not found"));
        Queue queue =
                queueRepository
                        .findById(request.queueId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST, "Queue not found"));
        category.setName(request.name());
        category.setQueue(queue);
        category.setActive(request.active());
        return toAdminCategory(category);
    }

    @Transactional
    public List<AdminCategoryResponse> listAllCategories() {
        return categoryRepository.findAll().stream().map(this::toAdminCategory).toList();
    }

    private AdminQueueResponse toAdminQueue(Queue q) {
        return new AdminQueueResponse(q.getId(), q.getName(), q.getDescription(), q.isActive());
    }

    private AdminCategoryResponse toAdminCategory(Category c) {
        return new AdminCategoryResponse(
                c.getId(), c.getName(), c.getQueue().getId(), c.getQueue().getName(), c.isActive());
    }

    public List<SlaPolicyResponse> listSlaPolicies() {
        return slaPolicyRepository.findAll().stream()
                .map(
                        p ->
                                new SlaPolicyResponse(
                                        p.getId(),
                                        p.getPriority(),
                                        p.getFirstResponseMinutes(),
                                        p.getResolutionMinutes()))
                .toList();
    }

    @Transactional
    public SlaPolicyResponse updateSlaPolicy(Long id, UpdateSlaPolicyRequest request) {
        SlaPolicy policy =
                slaPolicyRepository
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "SLA policy not found"));

        policy.setFirstResponseMinutes(request.firstResponseMinutes());
        policy.setResolutionMinutes(request.resolutionMinutes());
        return new SlaPolicyResponse(
                policy.getId(),
                policy.getPriority(),
                policy.getFirstResponseMinutes(),
                policy.getResolutionMinutes());
    }

    @Transactional
    public TicketResponse assignTicket(
            Long ticketId, Long actingUserId, AssignTicketRequest request) {

        Ticket ticket =
                ticketRepository
                        .findById(ticketId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Ticket not found"));

        User actor =
                userRepository
                        .findById(actingUserId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Acting user not found"));

        User newAssignee =
                userRepository
                        .findById(request.assigneeId())
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Assignee not found"));

        boolean assigneeHasEligibleRole =
                newAssignee.getRoles().stream()
                        .anyMatch(
                                r ->
                                        r.getName().equals("ROLE_AGENT")
                                                || r.getName().equals("ROLE_MANAGER"));

        if (!assigneeHasEligibleRole) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Tickets can only be assigned to an agent or manager");
        }

        boolean inSameQueue =
                newAssignee.getQueues().stream()
                        .anyMatch(q -> q.getId().equals(ticket.getQueue().getId()));

        if (!inSameQueue) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Assignee is not a member of this ticket's queue");
        }
        Instant now = Instant.now();

        User previousAssignee = ticket.getAssignee();
        ticket.setAssignee(newAssignee);
        ticket.setUpdatedAt(now);

        auditEntryRepository.save(
                AuditEntry.builder()
                        .ticket(ticket)
                        .actor(actor)
                        .field("assignee")
                        .oldValue(previousAssignee != null ? previousAssignee.getEmail() : null)
                        .newValue(newAssignee.getEmail())
                        .createdAt(now)
                        .build());

        if (ticket.getStatus() == TicketStatus.NEW) {
            TicketStatus oldStatus = ticket.getStatus();
            ticket.setStatus(TicketStatus.ASSIGNED);

            auditEntryRepository.save(
                    AuditEntry.builder()
                            .ticket(ticket)
                            .actor(actor)
                            .field("status")
                            .oldValue(oldStatus.name())
                            .newValue(TicketStatus.ASSIGNED.name())
                            .createdAt(now)
                            .build());
        }

        notificationService.notifyTicketAssigned(ticket);
        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> searchTickets(
            UserPrincipal caller, TicketSearchCriteria criteria, Pageable pageable) {
        List<String> callerRoles =
                caller.getAuthorities().stream().map(a -> a.getAuthority()).toList();

        boolean isRequesterOnly =
                callerRoles.stream().allMatch(role -> role.equals("ROLE_REQUESTER"));

        boolean isAgentOnly =
                callerRoles.contains("ROLE_AGENT")
                        && !callerRoles.contains("ROLE_MANAGER")
                        && !callerRoles.contains("ROLE_ADMIN");

        Long effectiveRequesterId = isRequesterOnly ? caller.getId() : null;

        Specification<Ticket> spec =
                Specification.where(TicketSpecifications.notDeleted())
                        .and(TicketSpecifications.hasStatus(criteria.status()))
                        .and(TicketSpecifications.hasPriority(criteria.priority()))
                        .and(TicketSpecifications.hasQueue(criteria.queueId()))
                        .and(TicketSpecifications.hasAssignee(criteria.assigneeId()))
                        .and(TicketSpecifications.hasCategory(criteria.categoryId()))
                        .and(TicketSpecifications.hasRequester(effectiveRequesterId))
                        .and(
                                TicketSpecifications.createdBetween(
                                        criteria.createdFrom(), criteria.createdTo()))
                        .and(TicketSpecifications.searchText(criteria.search()))
                        .and(TicketSpecifications.triageOrder());

        if (isAgentOnly) {
            User agent =
                    userRepository
                            .findById(caller.getId())
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND, "User not found"));

            List<Long> agentQueueIds = agent.getQueues().stream().map(Queue::getId).toList();

            spec = spec.and(TicketSpecifications.hasQueueIn(agentQueueIds));
        }

        return ticketRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<AuditEntryResponse> getAuditHistory(Long ticketId) {
        ticketRepository
                .findById(ticketId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Ticket not found"));

        return auditEntryRepository.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                .map(this::toAuditResponse)
                .toList();
    }

    @Transactional
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findByActiveTrue().stream()
                .map(c -> new CategoryResponse(c.getId(), c.getName(), c.getQueue().getName()))
                .toList();
    }

    @Transactional
    public List<QueueResponse> listQueues() {
        return queueRepository.findAll().stream()
                .filter(Queue::isActive)
                .map(q -> new QueueResponse(q.getId(), q.getName()))
                .toList();
    }

    private AuditEntryResponse toAuditResponse(AuditEntry entry) {
        return new AuditEntryResponse(
                entry.getId(),
                entry.getField(),
                entry.getOldValue(),
                entry.getNewValue(),
                entry.getActor().getId(),
                entry.getActor().getFullName(),
                entry.getCreatedAt());
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
                ticket.getQueue().getId(),
                ticket.getQueue().getName(),
                ticket.getRequester().getId(),
                ticket.getRequester().getFullName(),
                ticket.getAssignee() != null ? ticket.getAssignee().getId() : null,
                ticket.getAssignee() != null ? ticket.getAssignee().getFullName() : null,
                ticket.getCreatedAt(),
                ticket.getSlaDueAt(),
                ticket.getResolvedAt(),
                ticket.getClosedAt());
    }
}
