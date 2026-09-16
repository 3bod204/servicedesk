package com.tcc.servicedesk.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationSenderJob {

    private static final int MAX_ATTEMPTS = 3;

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public NotificationSenderJob(
            NotificationRepository notificationRepository,
            JavaMailSender mailSender,
            @Value("${app.mail.from:noreply@servicedesk.local}") String fromAddress) {
        this.notificationRepository = notificationRepository;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "notification-sender", lockAtLeastFor = "10s", lockAtMostFor = "25s")
    public void sendPending() {
        List<Notification> due = notificationRepository.findDueForSending(Instant.now());

        for (Notification notification : due) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress);
                message.setTo(notification.getRecipient());
                message.setSubject(notification.getSubject());
                message.setText(notification.getBody());

                mailSender.send(message);

                notification.setStatus("SENT");
                notification.setSentAt(Instant.now());

            } catch (Exception e) {
                int attempts = notification.getAttempts() + 1;
                notification.setAttempts(attempts);
                notification.setLastError(e.getMessage());

                if (attempts >= MAX_ATTEMPTS) {
                    notification.setStatus("FAILED");
                } else {
                    long backoffMinutes = (long) Math.pow(2, attempts);
                    notification.setNextRetryAt(
                            Instant.now().plus(backoffMinutes, ChronoUnit.MINUTES));
                }
            }

            notificationRepository.save(notification);
        }
    }
}
