package com.tcc.servicedesk.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByQueues_IdAndActiveTrueAndDeletedFalse(Long queueId);

    List<User> findByQueues_IdAndActiveTrueAndDeletedFalseAndRoles_NameIn(
            Long queueId, List<String> roleNames);

    List<User> findByActiveTrueAndDeletedFalseAndRoles_NameInOrderByFullNameAsc(
            List<String> roleNames);
}
