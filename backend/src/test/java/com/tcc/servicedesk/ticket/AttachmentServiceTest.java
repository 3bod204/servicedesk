package com.tcc.servicedesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tcc.servicedesk.security.UserPrincipal;
import com.tcc.servicedesk.storage.StorageService;
import com.tcc.servicedesk.ticket.dto.AttachmentResponse;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock private AttachmentRepository attachmentRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditEntryRepository auditEntryRepository;
    @Mock private StorageService storageService;

    private final FileContentValidator fileContentValidator = new FileContentValidator();

    private AttachmentService attachmentService;

    private User requester;
    private User agent;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(
                attachmentRepository,
                ticketRepository,
                userRepository,
                auditEntryRepository,
                storageService,
                fileContentValidator);

        requester = User.builder().id(1L).email("req@test.com").fullName("Req Ester").build();
        agent = User.builder().id(2L).email("agent@test.com").fullName("Agent Smith").build();
        ticket = Ticket.builder().id(10L).requester(requester).build();
    }

    private UserPrincipal principalWithRoles(User user, String... roleNames) {
        user.setRoles(Arrays.stream(roleNames)
                .map(name -> Role.builder().id(1L).name(name).build())
                .collect(Collectors.toSet()));
        return new UserPrincipal(user);
    }

    private static final byte[] PDF_BYTES = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31};

    @Test
    void uploadSucceedsForOwningRequester() {
        UserPrincipal caller = principalWithRoles(requester, "ROLE_REQUESTER");
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_BYTES);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(1L)).thenReturn(Optional.of(requester));
        when(attachmentRepository.countByTicketIdAndDeletedFalse(10L)).thenReturn(0L);

        AttachmentResponse response = attachmentService.uploadAttachment(10L, caller, file);

        assertThat(response.filename()).isEqualTo("doc.pdf");
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(response.uploadedById()).isEqualTo(1L);
        verify(storageService).upload(anyString(), any(byte[].class), org.mockito.ArgumentMatchers.eq("application/pdf"));
        verify(attachmentRepository).save(any(Attachment.class));
        verify(auditEntryRepository).save(any(AuditEntry.class));
    }

    @Test
    void uploadRejectsRequesterUploadingToSomeoneElsesTicket() {
        User otherRequester = User.builder().id(99L).email("other@test.com").fullName("Other").build();
        UserPrincipal caller = principalWithRoles(otherRequester, "ROLE_REQUESTER");
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_BYTES);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(99L)).thenReturn(Optional.of(otherRequester));

        assertThatThrownBy(() -> attachmentService.uploadAttachment(10L, caller, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("your own tickets");

        verify(storageService, never()).upload(anyString(), any(byte[].class), anyString());
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void uploadAllowsAgentOnAnyTicket() {
        UserPrincipal caller = principalWithRoles(agent, "ROLE_AGENT");
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_BYTES);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
        when(attachmentRepository.countByTicketIdAndDeletedFalse(10L)).thenReturn(0L);

        AttachmentResponse response = attachmentService.uploadAttachment(10L, caller, file);

        assertThat(response).isNotNull();
    }

    @Test
    void uploadRejectsWhenTicketNotFound() {
        UserPrincipal caller = principalWithRoles(agent, "ROLE_AGENT");
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_BYTES);

        when(ticketRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> attachmentService.uploadAttachment(10L, caller, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ticket not found");
    }

    @Test
    void uploadRejectsWhenAtMaxAttachmentLimit() {
        UserPrincipal caller = principalWithRoles(agent, "ROLE_AGENT");
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_BYTES);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
        when(attachmentRepository.countByTicketIdAndDeletedFalse(10L)).thenReturn(5L);

        assertThatThrownBy(() -> attachmentService.uploadAttachment(10L, caller, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("maximum of 5");
    }

    @Test
    void uploadRejectsEmptyFile() {
        UserPrincipal caller = principalWithRoles(agent, "ROLE_AGENT");
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[0]);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
        when(attachmentRepository.countByTicketIdAndDeletedFalse(10L)).thenReturn(0L);

        assertThatThrownBy(() -> attachmentService.uploadAttachment(10L, caller, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void uploadRejectsFileOverSizeLimit() {
        UserPrincipal caller = principalWithRoles(agent, "ROLE_AGENT");
        byte[] tooBig = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", tooBig);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
        when(attachmentRepository.countByTicketIdAndDeletedFalse(10L)).thenReturn(0L);

        assertThatThrownBy(() -> attachmentService.uploadAttachment(10L, caller, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("10 MB limit");
    }

    @Test
    void uploadRejectsDisallowedExtension() {
        UserPrincipal caller = principalWithRoles(agent, "ROLE_AGENT");
        MockMultipartFile file = new MockMultipartFile("file", "script.exe", "application/octet-stream", PDF_BYTES);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
        when(attachmentRepository.countByTicketIdAndDeletedFalse(10L)).thenReturn(0L);

        assertThatThrownBy(() -> attachmentService.uploadAttachment(10L, caller, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void uploadRejectsContentThatDoesNotMatchExtension() {
        UserPrincipal caller = principalWithRoles(agent, "ROLE_AGENT");
        byte[] textBytes = "just plain text".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", textBytes);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(2L)).thenReturn(Optional.of(agent));
        when(attachmentRepository.countByTicketIdAndDeletedFalse(10L)).thenReturn(0L);

        assertThatThrownBy(() -> attachmentService.uploadAttachment(10L, caller, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not match its extension");
    }

    @Test
    void listRejectsRequesterViewingSomeoneElsesTicket() {
        User otherRequester = User.builder().id(99L).email("other@test.com").fullName("Other").build();
        UserPrincipal caller = principalWithRoles(otherRequester, "ROLE_REQUESTER");

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));

        assertThatThrownBy(() -> attachmentService.listAttachments(10L, caller))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("your own tickets");
    }

    @Test
    void listReturnsAttachmentsForOwningRequester() {
        UserPrincipal caller = principalWithRoles(requester, "ROLE_REQUESTER");
        Attachment attachment = Attachment.builder()
                .id(5L)
                .ticket(ticket)
                .filename("a.pdf")
                .contentType("application/pdf")
                .sizeBytes(100)
                .storageKey("key")
                .uploadedBy(requester)
                .build();

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(attachmentRepository.findByTicketIdAndDeletedFalse(10L)).thenReturn(List.of(attachment));

        List<AttachmentResponse> result = attachmentService.listAttachments(10L, caller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).filename()).isEqualTo("a.pdf");
    }

    @Test
    void downloadRejectsRequesterAccessingSomeoneElsesTicketAttachment() {
        User otherRequester = User.builder().id(99L).email("other@test.com").fullName("Other").build();
        UserPrincipal caller = principalWithRoles(otherRequester, "ROLE_REQUESTER");
        Attachment attachment = Attachment.builder()
                .id(5L)
                .ticket(ticket)
                .filename("a.pdf")
                .contentType("application/pdf")
                .storageKey("key")
                .uploadedBy(requester)
                .build();

        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(attachment));

        assertThatThrownBy(() -> attachmentService.downloadAttachment(5L, caller))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("your own tickets");

        verify(storageService, never()).download(anyString());
    }

    @Test
    void downloadSucceedsAndReturnsContent() {
        UserPrincipal caller = principalWithRoles(requester, "ROLE_REQUESTER");
        Attachment attachment = Attachment.builder()
                .id(5L)
                .ticket(ticket)
                .filename("a.pdf")
                .contentType("application/pdf")
                .storageKey("key")
                .uploadedBy(requester)
                .build();

        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(attachment));
        when(storageService.download("key")).thenReturn(PDF_BYTES);

        AttachmentService.DownloadedFile result = attachmentService.downloadAttachment(5L, caller);

        assertThat(result.filename()).isEqualTo("a.pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.content()).isEqualTo(PDF_BYTES);
    }
}
