package com.tcc.servicedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tcc.servicedesk.security.UserPrincipal;
import com.tcc.servicedesk.ticket.dto.CommentResponse;
import com.tcc.servicedesk.ticket.dto.CreateCommentRequest;
import com.tcc.servicedesk.user.Role;
import com.tcc.servicedesk.user.User;
import com.tcc.servicedesk.user.UserRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditEntryRepository auditEntryRepository;

    private CommentService commentService;

    private User requester;
    private User agent;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        commentService =
                new CommentService(
                        commentRepository, ticketRepository, userRepository, auditEntryRepository);

        requester = User.builder().id(1L).email("req@test.com").fullName("Req Ester").build();
        agent = User.builder().id(2L).email("agent@test.com").fullName("Agent Smith").build();
        ticket = Ticket.builder().id(10L).requester(requester).build();
    }

    private UserPrincipal principalWithRoles(User user, String... roleNames) {
        user.setRoles(
                Arrays.stream(roleNames)
                        .map(name -> Role.builder().id(1L).name(name).build())
                        .collect(Collectors.toSet()));
        return new UserPrincipal(user);
    }

    @Test
    void requesterCanCreatePublicCommentOnOwnTicket() {
        UserPrincipal caller = principalWithRoles(requester, "ROLE_REQUESTER");
        CreateCommentRequest request = new CreateCommentRequest("hello", false, null);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));

        CommentResponse response = commentService.createComment(10L, caller, request);

        assertThat(response.body()).isEqualTo("hello");
        assertThat(response.internal()).isFalse();
        verify(commentRepository).save(org.mockito.ArgumentMatchers.any(Comment.class));
        verify(auditEntryRepository).save(org.mockito.ArgumentMatchers.any(AuditEntry.class));
    }

    @Test
    void requesterCannotCreateInternalComment() {
        UserPrincipal caller = principalWithRoles(requester, "ROLE_REQUESTER");
        CreateCommentRequest request = new CreateCommentRequest("secret", true, null);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));

        assertThatThrownBy(() -> commentService.createComment(10L, caller, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot create internal comments");

        verify(commentRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void requesterCannotCommentOnSomeoneElsesTicket() {
        User otherRequester =
                User.builder().id(99L).email("other@test.com").fullName("Other").build();
        UserPrincipal caller = principalWithRoles(otherRequester, "ROLE_REQUESTER");
        CreateCommentRequest request = new CreateCommentRequest("hello", false, null);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(99L)).thenReturn(Optional.of(otherRequester));

        assertThatThrownBy(() -> commentService.createComment(10L, caller, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("your own tickets");
    }

    @Test
    void agentCanCreateInternalComment() {
        UserPrincipal caller = principalWithRoles(agent, "ROLE_AGENT");
        CreateCommentRequest request = new CreateCommentRequest("internal note", true, null);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(agent));

        CommentResponse response = commentService.createComment(10L, caller, request);

        assertThat(response.internal()).isTrue();
    }

    @Test
    void createCommentRejectsParentFromDifferentTicket() {
        UserPrincipal caller = principalWithRoles(agent, "ROLE_AGENT");
        Ticket otherTicket = Ticket.builder().id(20L).requester(requester).build();
        Comment parent =
                Comment.builder().id(3L).ticket(otherTicket).author(agent).body("p").build();
        CreateCommentRequest request = new CreateCommentRequest("reply", false, 3L);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
        when(commentRepository.findById(3L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.createComment(10L, caller, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("different ticket");
    }

    @Test
    void createCommentRejectsMissingParent() {
        UserPrincipal caller = principalWithRoles(agent, "ROLE_AGENT");
        CreateCommentRequest request = new CreateCommentRequest("reply", false, 999L);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.createComment(10L, caller, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Parent comment not found");
    }

    @Test
    void listCommentsHidesInternalCommentsFromRequester() {
        UserPrincipal caller = principalWithRoles(requester, "ROLE_REQUESTER");

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findByTicketIdAndInternalFalseOrderByCreatedAtAsc(10L))
                .thenReturn(List.of());

        commentService.listComments(10L, caller);

        verify(commentRepository).findByTicketIdAndInternalFalseOrderByCreatedAtAsc(10L);
        verify(commentRepository, never()).findByTicketIdOrderByCreatedAtAsc(10L);
    }

    @Test
    void listCommentsIncludesInternalCommentsForAgent() {
        UserPrincipal caller = principalWithRoles(agent, "ROLE_AGENT");

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(10L)).thenReturn(List.of());

        commentService.listComments(10L, caller);

        verify(commentRepository).findByTicketIdOrderByCreatedAtAsc(10L);
        verify(commentRepository, never()).findByTicketIdAndInternalFalseOrderByCreatedAtAsc(10L);
    }

    @Test
    void listCommentsRejectsRequesterViewingSomeoneElsesTicket() {
        User otherRequester =
                User.builder().id(99L).email("other@test.com").fullName("Other").build();
        UserPrincipal caller = principalWithRoles(otherRequester, "ROLE_REQUESTER");

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> commentService.listComments(10L, caller))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("your own tickets");
    }
}
