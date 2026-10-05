package com.tcc.servicedesk.ticket.queue;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueueRepository extends JpaRepository<Queue, Long> {

    Optional<Queue> findByName(String name);
}
