package com.tcc.servicedesk.ticket;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket , Long> {

    Optional<Ticket> findByReference(String reference);
}
