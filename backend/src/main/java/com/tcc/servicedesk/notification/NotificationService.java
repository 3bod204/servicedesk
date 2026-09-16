package com.tcc.servicedesk.notification;

import com.tcc.servicedesk.ticket.Ticket;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void notifySlaBreach(Ticket ticket) {
        queue(
                ticket,
                "SLA_BREACH",
                ticket.getRequester().getEmail(),
                "SLA breached: " + ticket.getReference(),
                "Ticket "
                        + ticket.getReference()
                        + " ("
                        + ticket.getTitle()
                        + ") has breached its SLA resolution deadline.");
    }

    public void notifySlaWarning(Ticket ticket) {
        queue(
                ticket,
                "SLA_WARNING",
                ticket.getRequester().getEmail(),
                "SLA warning: " + ticket.getReference(),
                "Ticket "
                        + ticket.getReference()
                        + " ("
                        + ticket.getTitle()
                        + ") is approaching its SLA resolution deadline.");
    }

    public void notifyTicketCreated(Ticket ticket) {
        queue(
                ticket,
                "TICKET_CREATED",
                ticket.getRequester().getEmail(),
                "Ticket created: " + ticket.getReference(),
                "Your ticket \""
                        + ticket.getTitle()
                        + "\" has been created as "
                        + ticket.getReference()
                        + ".");
    }

    public void notifyTicketAssigned(Ticket ticket) {
        if (ticket.getAssignee() == null) {
            return;
        }
        queue(
                ticket,
                "TICKET_ASSIGNED",
                ticket.getAssignee().getEmail(),
                "Ticket assigned: " + ticket.getReference(),
                "Ticket " + ticket.getReference() + " has been assigned to you.");
    }

    public void notifyTicketResolved(Ticket ticket) {
        queue(
                ticket,
                "TICKET_RESOLVED",
                ticket.getRequester().getEmail(),
                "Ticket resolved: " + ticket.getReference(),
                "Your ticket " + ticket.getReference() + " has been marked resolved.");
    }

    public void notifyCommentAdded(Ticket ticket, String commentAuthorName) {
        queue(
                ticket,
                "COMMENT_ADDED",
                ticket.getRequester().getEmail(),
                "New comment on " + ticket.getReference(),
                commentAuthorName
                        + " added a comment to your ticket "
                        + ticket.getReference()
                        + ".");
    }

    private void queue(
            Ticket ticket, String eventType, String recipient, String subject, String body) {
        Notification notification =
                Notification.builder()
                        .ticketId(ticket.getId())
                        .eventType(eventType)
                        .recipient(recipient)
                        .subject(subject)
                        .body(body)
                        .build();

        notificationRepository.save(notification);
    }
}
