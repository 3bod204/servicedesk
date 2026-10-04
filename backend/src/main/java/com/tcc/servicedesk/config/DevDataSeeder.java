package com.tcc.servicedesk.config;

import com.tcc.servicedesk.ticket.Category;
import com.tcc.servicedesk.ticket.CategoryRepository;
import com.tcc.servicedesk.ticket.Priority;
import com.tcc.servicedesk.ticket.Queue;
import com.tcc.servicedesk.ticket.QueueRepository;
import com.tcc.servicedesk.ticket.SlaPolicy;
import com.tcc.servicedesk.ticket.SlaPolicyRepository;
import com.tcc.servicedesk.ticket.Ticket;
import com.tcc.servicedesk.ticket.TicketReferenceGenerator;
import com.tcc.servicedesk.ticket.TicketRepository;
import com.tcc.servicedesk.ticket.TicketStatus;
import com.tcc.servicedesk.user.Role;
import com.tcc.servicedesk.user.RoleRepository;
import com.tcc.servicedesk.user.User;
import com.tcc.servicedesk.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
public class DevDataSeeder implements CommandLineRunner {

    private static final int TICKET_COUNT = 180;
    private static final String SEED_PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final QueueRepository queueRepository;
    private final CategoryRepository categoryRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final TicketRepository ticketRepository;
    private final TicketReferenceGenerator referenceGenerator;
    private final PasswordEncoder passwordEncoder;
    private final Random random = new Random();

    public DevDataSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            QueueRepository queueRepository,
            CategoryRepository categoryRepository,
            SlaPolicyRepository slaPolicyRepository,
            TicketRepository ticketRepository,
            TicketReferenceGenerator referenceGenerator,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.queueRepository = queueRepository;
        this.categoryRepository = categoryRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.ticketRepository = ticketRepository;
        this.referenceGenerator = referenceGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedAdmin();
        seedSampleData();
    }

    private void seedAdmin() {
        if (userRepository.existsByEmailIgnoreCase("agent@tcc.sa")) {
            return;
        }

        User user =
                User.builder()
                        .email("agent@tcc.sa")
                        .passwordHash(passwordEncoder.encode(SEED_PASSWORD))
                        .fullName("Test Agent")
                        .active(true)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

        Role adminRole =
                roleRepository
                        .findByName("ROLE_ADMIN")
                        .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not seeded"));
        user.getRoles().add(adminRole);

        userRepository.save(user);
        System.out.println("Seeded test user: agent@tcc.sa / " + SEED_PASSWORD + " (ROLE_ADMIN)");
    }

    private void seedSampleData() {
        if (userRepository.existsByEmailIgnoreCase("sara.agent@tcc.sa")) {
            return;
        }

        Role agentRole = requireRole("ROLE_AGENT");
        Role managerRole = requireRole("ROLE_MANAGER");
        Role requesterRole = requireRole("ROLE_REQUESTER");

        Queue serviceDesk = requireQueue("Service Desk");
        Queue network = requireQueue("Network");
        Queue applications = requireQueue("Applications");

        User manager = createUser("manager@tcc.sa", "Maya Haddad", managerRole);

        User sara = createUser("sara.agent@tcc.sa", "Sara Haddad", agentRole);
        sara.getQueues().add(serviceDesk);

        User omar = createUser("omar.agent@tcc.sa", "Omar Nasser", agentRole);
        omar.getQueues().add(serviceDesk);

        User lina = createUser("lina.agent@tcc.sa", "Lina Fares", agentRole);
        lina.getQueues().add(network);

        User yousef = createUser("yousef.agent@tcc.sa", "Yousef Saleh", agentRole);
        yousef.getQueues().add(applications);

        User noor = createUser("noor.agent@tcc.sa", "Noor Aziz", agentRole);
        noor.getQueues().add(applications);
        noor.getQueues().add(network);

        userRepository.saveAll(List.of(manager, sara, omar, lina, yousef, noor));

        Map<Long, List<User>> agentsByQueue =
                Map.of(
                        serviceDesk.getId(), List.of(sara, omar),
                        network.getId(), List.of(lina, noor),
                        applications.getId(), List.of(yousef, noor));

        String[] requesterNames = {
            "Ahmad Youssef", "Rana Qasem", "Khalid Odeh", "Hiba Saad", "Tariq Amer",
            "Dana Khalil", "Faris Jaber", "Lama Suleiman", "Bilal Hourani", "Mona Rasheed"
        };
        List<User> requesters = new ArrayList<>();
        for (int i = 0; i < requesterNames.length; i++) {
            requesters.add(
                    createUser(
                            "requester" + (i + 1) + "@tcc.sa", requesterNames[i], requesterRole));
        }
        userRepository.saveAll(requesters);

        List<Category> categories = categoryRepository.findByActiveTrue();
        TicketStatus[] statusPool = buildWeightedStatusPool();
        Priority[] priorityPool = buildWeightedPriorityPool();
        Instant now = Instant.now();

        for (int i = 0; i < TICKET_COUNT; i++) {
            Category category = categories.get(random.nextInt(categories.size()));
            Queue queue = category.getQueue();
            Priority priority = priorityPool[random.nextInt(priorityPool.length)];
            TicketStatus status = statusPool[random.nextInt(statusPool.length)];
            User requester = requesters.get(random.nextInt(requesters.size()));
            SlaPolicy sla =
                    slaPolicyRepository
                            .findByPriority(priority)
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "No SLA policy for " + priority));

            int ageMinutes = random.nextInt(60 * 24 * 60);
            Instant createdAt = now.minus(ageMinutes, ChronoUnit.MINUTES);
            Instant firstResponseDueAt =
                    createdAt.plus(sla.getFirstResponseMinutes(), ChronoUnit.MINUTES);
            Instant slaDueAt = createdAt.plus(sla.getResolutionMinutes(), ChronoUnit.MINUTES);

            Instant updatedAt = createdAt;
            Instant firstResponseAt = null;
            Instant resolvedAt = null;
            Instant closedAt = null;
            Instant pendingSince = null;
            int pendingMinutesTotal = 0;
            boolean firstResponseBreached = false;
            boolean resolutionBreached = false;
            User assignee = null;

            List<User> queueAgents = agentsByQueue.getOrDefault(queue.getId(), List.of());

            if (status != TicketStatus.NEW && !queueAgents.isEmpty()) {
                assignee = queueAgents.get(random.nextInt(queueAgents.size()));

                boolean breachFirstResponse = random.nextInt(100) < 20;
                int frBound = sla.getFirstResponseMinutes() * (breachFirstResponse ? 2 : 1) + 5;
                int frMinutes = random.nextInt(frBound) + 1;
                long ageAvailable = Math.max(0, ChronoUnit.MINUTES.between(createdAt, now));
                firstResponseAt =
                        createdAt.plus(Math.min(frMinutes, ageAvailable), ChronoUnit.MINUTES);
                firstResponseBreached = firstResponseAt.isAfter(firstResponseDueAt);
                updatedAt = firstResponseAt;
            }

            if (status == TicketStatus.PENDING_REQUESTER && firstResponseAt != null) {
                long ageAvailable = Math.max(0, ChronoUnit.MINUTES.between(firstResponseAt, now));
                pendingSince =
                        firstResponseAt.plus(
                                Math.min(random.nextInt(500) + 10, ageAvailable),
                                ChronoUnit.MINUTES);
                updatedAt = pendingSince;
            }

            if ((status == TicketStatus.RESOLVED
                            || status == TicketStatus.CLOSED
                            || status == TicketStatus.REOPENED)
                    && firstResponseAt != null) {
                pendingMinutesTotal = random.nextInt(240);
                boolean breachResolution = random.nextInt(100) < 20;
                int resBound = sla.getResolutionMinutes() * (breachResolution ? 2 : 1) + 30;
                int resMinutes = random.nextInt(resBound) + 30;
                long ageAvailable = Math.max(0, ChronoUnit.MINUTES.between(firstResponseAt, now));
                resolvedAt =
                        firstResponseAt.plus(
                                Math.min(resMinutes, ageAvailable), ChronoUnit.MINUTES);
                long resolutionMinutes =
                        ChronoUnit.MINUTES.between(createdAt, resolvedAt) - pendingMinutesTotal;
                resolutionBreached = resolutionMinutes > sla.getResolutionMinutes();
                updatedAt = resolvedAt;
            }

            if (status == TicketStatus.CLOSED && resolvedAt != null) {
                long ageAvailable = Math.max(0, ChronoUnit.MINUTES.between(resolvedAt, now));
                closedAt =
                        resolvedAt.plus(
                                Math.min(random.nextInt(60 * 24 * 5) + 30, ageAvailable),
                                ChronoUnit.MINUTES);
                updatedAt = closedAt;
            }

            if (status == TicketStatus.REOPENED && resolvedAt != null) {
                long ageAvailable = Math.max(0, ChronoUnit.MINUTES.between(resolvedAt, now));
                updatedAt =
                        resolvedAt.plus(
                                Math.min(random.nextInt(60 * 24 * 10) + 60, ageAvailable),
                                ChronoUnit.MINUTES);
            }

            Ticket ticket =
                    Ticket.builder()
                            .reference(referenceGenerator.generate())
                            .title(randomTitle(category.getName()))
                            .description(
                                    "Seed data ticket for "
                                            + category.getName()
                                            + ", reported by "
                                            + requester.getFullName()
                                            + ".")
                            .status(status)
                            .priority(priority)
                            .category(category)
                            .queue(queue)
                            .requester(requester)
                            .assignee(assignee)
                            .createdAt(createdAt)
                            .updatedAt(updatedAt)
                            .firstResponseAt(firstResponseAt)
                            .resolvedAt(resolvedAt)
                            .closedAt(closedAt)
                            .firstResponseDueAt(firstResponseDueAt)
                            .slaDueAt(slaDueAt)
                            .pendingSince(pendingSince)
                            .pendingMinutesTotal(pendingMinutesTotal)
                            .firstResponseBreached(firstResponseBreached)
                            .resolutionBreached(resolutionBreached)
                            .build();

            ticketRepository.save(ticket);
        }

        System.out.println(
                "Seeded "
                        + TICKET_COUNT
                        + " sample tickets, "
                        + requesters.size()
                        + " requesters, and 5 agents across 3 queues.");
        System.out.println(
                "Extra logins (all "
                        + SEED_PASSWORD
                        + "): manager@tcc.sa (ROLE_MANAGER), "
                        + "sara.agent@tcc.sa / omar.agent@tcc.sa (Service Desk), "
                        + "lina.agent@tcc.sa (Network), yousef.agent@tcc.sa / noor.agent@tcc.sa (Applications)");
    }

    private User createUser(String email, String fullName, Role role) {
        Instant now = Instant.now();
        User user =
                User.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode(SEED_PASSWORD))
                        .fullName(fullName)
                        .active(true)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
        user.getRoles().add(role);
        return user;
    }

    private Role requireRole(String name) {
        return roleRepository
                .findByName(name)
                .orElseThrow(() -> new IllegalStateException(name + " not seeded"));
    }

    private Queue requireQueue(String name) {
        return queueRepository
                .findByName(name)
                .orElseThrow(() -> new IllegalStateException("Queue not seeded: " + name));
    }

    private TicketStatus[] buildWeightedStatusPool() {
        List<TicketStatus> pool = new ArrayList<>();
        addWeighted(pool, TicketStatus.NEW, 8);
        addWeighted(pool, TicketStatus.ASSIGNED, 12);
        addWeighted(pool, TicketStatus.IN_PROGRESS, 15);
        addWeighted(pool, TicketStatus.PENDING_REQUESTER, 10);
        addWeighted(pool, TicketStatus.RESOLVED, 30);
        addWeighted(pool, TicketStatus.CLOSED, 20);
        addWeighted(pool, TicketStatus.REOPENED, 5);
        return pool.toArray(new TicketStatus[0]);
    }

    private Priority[] buildWeightedPriorityPool() {
        List<Priority> pool = new ArrayList<>();
        addWeighted(pool, Priority.LOW, 25);
        addWeighted(pool, Priority.MEDIUM, 40);
        addWeighted(pool, Priority.HIGH, 25);
        addWeighted(pool, Priority.URGENT, 10);
        return pool.toArray(new Priority[0]);
    }

    private <T> void addWeighted(List<T> pool, T value, int weight) {
        for (int i = 0; i < weight; i++) {
            pool.add(value);
        }
    }

    private String randomTitle(String categoryName) {
        String[] templates = {
            "Issue with " + categoryName,
            categoryName + " request",
            "Need help: " + categoryName,
            categoryName + " not working as expected",
            "Question about " + categoryName
        };
        return templates[random.nextInt(templates.length)];
    }
}
