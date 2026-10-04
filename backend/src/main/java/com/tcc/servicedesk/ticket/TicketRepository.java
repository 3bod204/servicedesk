package com.tcc.servicedesk.ticket;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TicketRepository
        extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByReference(String reference);

    List<Ticket> findAllBySlaDueAtIsNotNullAndResolvedAtIsNullAndDeletedFalse();

    long countByStatusAndDeletedFalse(TicketStatus status);

    long countByPriorityAndDeletedFalse(Priority priority);

    List<Ticket> findAllByDeletedFalseAndFirstResponseAtIsNotNull();

    List<Ticket> findAllByDeletedFalseAndResolvedAtIsNotNull();

    long countByDeletedFalseAndResolvedAtIsNotNull();

    long countByDeletedFalseAndResolvedAtIsNotNullAndResolutionBreachedFalse();

    List<Ticket> findAllByDeletedFalseAndAssigneeIsNotNullAndStatusNotIn(
            List<TicketStatus> excludedStatuses);

    List<Ticket> findAllByDeletedFalse();
}
