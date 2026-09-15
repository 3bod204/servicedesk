package com.tcc.servicedesk.ticket;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    Optional<SlaPolicy> findByPriority(Priority priority);
}
