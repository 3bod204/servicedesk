package com.tcc.servicedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcc.servicedesk.TestcontainersConfiguration;
import com.tcc.servicedesk.security.UserPrincipal;
import com.tcc.servicedesk.ticket.dto.CreateTicketRequest;
import com.tcc.servicedesk.ticket.dto.TicketResponse;
import com.tcc.servicedesk.ticket.dto.TicketSearchCriteria;
import com.tcc.servicedesk.user.Role;
import com.tcc.servicedesk.user.RoleRepository;
import com.tcc.servicedesk.user.User;
import com.tcc.servicedesk.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises TicketService#searchTickets against a real Postgres instance (via Testcontainers)
 * because the agent queue-scoping behavior is expressed as a JPA Specification and cannot be
 * meaningfully verified with mocks.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TicketServiceSearchIntegrationTest {

    @Autowired private TicketService ticketService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private QueueRepository queueRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Queue serviceDeskQueue;
    private Queue networkQueue;
    private Category serviceDeskCategory;
    private Category networkCategory;
    private User requester;

    @BeforeEach
    void setUp() {
        serviceDeskQueue = queueRepository.findByName("Service Desk").orElseThrow();
        networkQueue = queueRepository.findByName("Network").orElseThrow();

        serviceDeskCategory =
                categoryRepository.findAll().stream()
                        .filter(c -> c.getQueue().getId().equals(serviceDeskQueue.getId()))
                        .findFirst()
                        .orElseThrow();
        networkCategory =
                categoryRepository.findAll().stream()
                        .filter(c -> c.getQueue().getId().equals(networkQueue.getId()))
                        .findFirst()
                        .orElseThrow();

        requester =
                userRepository.save(
                        User.builder()
                                .email("requester-" + System.nanoTime() + "@test.com")
                                .passwordHash("hash")
                                .fullName("Test Requester")
                                .roles(
                                        Set.of(
                                                roleRepository
                                                        .findByName("ROLE_REQUESTER")
                                                        .orElseThrow()))
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build());

        ticketService.createTicket(
                requester.getId(),
                new CreateTicketRequest(
                        "Service desk issue", "desc", serviceDeskCategory.getId(), Priority.LOW));
        ticketService.createTicket(
                requester.getId(),
                new CreateTicketRequest(
                        "Network issue", "desc", networkCategory.getId(), Priority.LOW));
    }

    private User createUser(String emailPrefix, Set<Queue> queues, String... roleNames) {
        Set<Role> roles =
                Set.of(roleNames).stream()
                        .map(name -> roleRepository.findByName(name).orElseThrow())
                        .collect(java.util.stream.Collectors.toSet());

        return userRepository.save(
                User.builder()
                        .email(emailPrefix + "-" + System.nanoTime() + "@test.com")
                        .passwordHash("hash")
                        .fullName(emailPrefix)
                        .roles(roles)
                        .queues(queues)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());
    }

    private UserPrincipal principalFor(User user) {
        return new UserPrincipal(user);
    }

    @Test
    void agentScopedToOneQueueOnlySeesThatQueuesTickets() {
        User agent = createUser("agent", Set.of(serviceDeskQueue), "ROLE_AGENT");

        Pageable pageable = PageRequest.of(0, 20);
        TicketSearchCriteria criteria =
                new TicketSearchCriteria(null, null, null, null, null, null, null, null);

        List<TicketResponse> results =
                ticketService.searchTickets(principalFor(agent), criteria, pageable).getContent();

        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(t -> t.queueName().equals("Service Desk"));
    }

    @Test
    void agentWithNoQueueMembershipsSeesNoTickets() {
        User agent = createUser("lonelyagent", Set.of(), "ROLE_AGENT");

        Pageable pageable = PageRequest.of(0, 20);
        TicketSearchCriteria criteria =
                new TicketSearchCriteria(null, null, null, null, null, null, null, null);

        List<TicketResponse> results =
                ticketService.searchTickets(principalFor(agent), criteria, pageable).getContent();

        assertThat(results).isEmpty();
    }

    @Test
    void managerSeesTicketsAcrossAllQueuesRegardlessOfMembership() {
        User manager = createUser("manager", Set.of(serviceDeskQueue), "ROLE_MANAGER");

        Pageable pageable = PageRequest.of(0, 20);
        TicketSearchCriteria criteria =
                new TicketSearchCriteria(null, null, null, null, null, null, null, null);

        List<TicketResponse> results =
                ticketService.searchTickets(principalFor(manager), criteria, pageable).getContent();

        assertThat(results.stream().map(TicketResponse::queueName))
                .contains("Service Desk", "Network");
    }

    @Test
    void requesterOnlySeesOwnTickets() {
        User otherRequester = createUser("other", Set.of(), "ROLE_REQUESTER");

        ticketService.createTicket(
                otherRequester.getId(),
                new CreateTicketRequest(
                        "Other's ticket", "desc", serviceDeskCategory.getId(), Priority.LOW));

        Pageable pageable = PageRequest.of(0, 20);
        TicketSearchCriteria criteria =
                new TicketSearchCriteria(null, null, null, null, null, null, null, null);

        List<TicketResponse> results =
                ticketService
                        .searchTickets(principalFor(requester), criteria, pageable)
                        .getContent();

        assertThat(results).allMatch(t -> t.requesterId().equals(requester.getId()));
    }
}
