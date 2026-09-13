package com.tcc.servicedesk.ticket;

import com.tcc.servicedesk.security.UserPrincipal;
import com.tcc.servicedesk.ticket.dto.CreateTicketRequest;
import com.tcc.servicedesk.ticket.dto.TicketResponse;
import com.tcc.servicedesk.ticket.dto.UpdateStatusRequest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
            @Valid @RequestBody CreateTicketRequest request
    ) {
        return ticketService.createTicket(principal.getId(), request);
    }

    @PutMapping("/{id}/status")
    public TicketResponse changeStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateStatusRequest request
) {
    return ticketService.changeStatus(id, request);
}
}