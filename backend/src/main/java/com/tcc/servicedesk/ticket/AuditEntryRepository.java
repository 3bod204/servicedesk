package com.tcc.servicedesk.ticket;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
