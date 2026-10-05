package com.tcc.servicedesk.ticket;

import com.tcc.servicedesk.security.UserPrincipal;
import com.tcc.servicedesk.ticket.audit.dto.AuditEntryResponse;
import com.tcc.servicedesk.ticket.dto.AssignTicketRequest;
import com.tcc.servicedesk.ticket.dto.CreateTicketRequest;
import com.tcc.servicedesk.ticket.dto.TicketResponse;
import com.tcc.servicedesk.ticket.dto.TicketSearchCriteria;
import com.tcc.servicedesk.ticket.dto.UpdateStatusRequest;
import com.tcc.servicedesk.ticket.queue.dto.AdminCategoryResponse;
import com.tcc.servicedesk.ticket.queue.dto.AdminQueueResponse;
import com.tcc.servicedesk.ticket.queue.dto.CategoryResponse;
import com.tcc.servicedesk.ticket.queue.dto.QueueResponse;
import com.tcc.servicedesk.ticket.queue.dto.UpdateCategoryRequest;
import com.tcc.servicedesk.ticket.queue.dto.UpdateQueueRequest;
import com.tcc.servicedesk.ticket.sla.dto.SlaPolicyResponse;
import com.tcc.servicedesk.ticket.sla.dto.UpdateSlaPolicyRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse createTicket(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTicketRequest request) {
        return ticketService.createTicket(principal.getId(), request);
    }

    @PutMapping("/{id}/status")
    public TicketResponse changeStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ticketService.changeStatus(id, principal.getId(), request);
    }

    @GetMapping
    public Page<TicketResponse> searchTickets(
            @AuthenticationPrincipal UserPrincipal principal,
            @ModelAttribute TicketSearchCriteria criteria,
            Pageable pageable) {
        return ticketService.searchTickets(principal, criteria, pageable);
    }

    @GetMapping("/{id}/audit")
    public List<AuditEntryResponse> getAuditHistory(@PathVariable Long id) {
        return ticketService.getAuditHistory(id);
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('AGENT','MANAGER','ADMIN')")
    public TicketResponse assignTicket(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AssignTicketRequest request) {
        return ticketService.assignTicket(id, principal.getId(), request);
    }

    @GetMapping("/{id}")
    public TicketResponse getTicket(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ticketService.getById(id, principal);
    }

    @GetMapping("/categories")
    public List<CategoryResponse> getCategories() {
        return ticketService.listCategories();
    }

    @GetMapping("/queues")
    public List<QueueResponse> getQueues() {
        return ticketService.listQueues();
    }

    @PostMapping("/admin/queues")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminQueueResponse createQueue(@Valid @RequestBody UpdateQueueRequest request) {
        return ticketService.createQueue(request);
    }

    @PutMapping("/admin/queues/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminQueueResponse updateQueue(
            @PathVariable Long id, @Valid @RequestBody UpdateQueueRequest request) {
        return ticketService.updateQueue(id, request);
    }

    @GetMapping("/admin/queues")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminQueueResponse> listAllQueues() {
        return ticketService.listAllQueues();
    }

    @PostMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminCategoryResponse createCategory(@Valid @RequestBody UpdateCategoryRequest request) {
        return ticketService.createCategory(request);
    }

    @PutMapping("/admin/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminCategoryResponse updateCategory(
            @PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        return ticketService.updateCategory(id, request);
    }

    @GetMapping("/admin/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminCategoryResponse> listAllCategories() {
        return ticketService.listAllCategories();
    }

    @GetMapping("/admin/sla-policies")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SlaPolicyResponse> listSlaPolicies() {
        return ticketService.listSlaPolicies();
    }

    @PutMapping("/admin/sla-policies/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SlaPolicyResponse updateSlaPolicy(
            @PathVariable Long id, @Valid @RequestBody UpdateSlaPolicyRequest request) {
        return ticketService.updateSlaPolicy(id, request);
    }
}
