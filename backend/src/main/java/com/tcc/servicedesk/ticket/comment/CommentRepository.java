package com.tcc.servicedesk.ticket.comment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    List<Comment> findByTicketIdAndInternalFalseOrderByCreatedAtAsc(Long ticketId);
}
