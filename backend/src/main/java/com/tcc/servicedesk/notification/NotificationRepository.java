package com.tcc.servicedesk.notification;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query(
            "SELECT n FROM Notification n WHERE n.status = 'PENDING' "
                    + "AND (n.nextRetryAt IS NULL OR n.nextRetryAt <= :now) "
                    + "ORDER BY n.createdAt ASC")
    List<Notification> findDueForSending(@Param("now") Instant now);
}
