package com.tcc.servicedesk.auth;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    long countByEmailIgnoreCaseAndSuccessfulFalseAndAttemptedAtAfter(String email, Instant since);
}
