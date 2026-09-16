package com.tcc.servicedesk.ticket;

import com.tcc.servicedesk.notification.NotificationService;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlaSweepJob {

    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;

    public SlaSweepJob(TicketRepository ticketRepository, NotificationService notificationService) {
        this.ticketRepository = ticketRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = 5, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    @SchedulerLock(name = "sla-sweep", lockAtLeastFor = "1m", lockAtMostFor = "4m")
    public void sweep() {
        Instant now = Instant.now();

        List<Ticket> activeTickets =
                ticketRepository.findAllBySlaDueAtIsNotNullAndResolvedAtIsNullAndDeletedFalse();

        for (Ticket ticket : activeTickets) {
            long elapsedMinutes =
                    java.time.temporal.ChronoUnit.MINUTES.between(ticket.getCreatedAt(), now)
                            - ticket.getPendingMinutesTotal();

            long budgetMinutes =
                    java.time.temporal.ChronoUnit.MINUTES.between(
                            ticket.getCreatedAt(), ticket.getSlaDueAt());

            if (budgetMinutes <= 0) {
                continue;
            }

            double percentConsumed = (double) elapsedMinutes / budgetMinutes;

            if (percentConsumed >= 1.0 && !ticket.isResolutionBreached()) {
                ticket.setResolutionBreached(true);
                notificationService.notifySlaBreach(ticket);
            } else if (percentConsumed >= 0.8 && !ticket.isBreachWarningSent()) {
                ticket.setBreachWarningSent(true);
                notificationService.notifySlaWarning(ticket);
            }
        }
    }
}
