package com.tcc.servicedesk.ticket;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByTicketIdAndDeletedFalse(Long ticketId);

    long countByTicketIdAndDeletedFalse(Long ticketId);
}