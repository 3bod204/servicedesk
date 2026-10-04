package com.tcc.servicedesk.ticket;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByTicketIdAndDeletedFalse(Long ticketId);

    long countByTicketIdAndDeletedFalse(Long ticketId);
}
