package com.tcc.servicedesk.ticket;

import org.springframework.data.jpa.domain.Specification;
import java.time.Instant;
import java.util.List;

public final class TicketSpecifications {

    private TicketSpecifications() {
    }

    public static Specification<Ticket> hasStatus(TicketStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Ticket> hasPriority(Priority priority) {
        return (root, query, cb) ->
                priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    public static Specification<Ticket> hasQueue(Long queueId) {
        return (root, query, cb) ->
                queueId == null ? null : cb.equal(root.get("queue").get("id"), queueId);
    }

    public static Specification<Ticket> hasAssignee(Long assigneeId) {
        return (root, query, cb) ->
                assigneeId == null ? null : cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    public static Specification<Ticket> hasCategory(Long categoryId) {
        return (root, query, cb) ->
                categoryId == null ? null : cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Ticket> hasRequester(Long requesterId) {
        return (root, query, cb) ->
                requesterId == null ? null : cb.equal(root.get("requester").get("id"), requesterId);
    }

    public static Specification<Ticket> createdBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from == null && to == null) return null;
            if (from != null && to != null) return cb.between(root.get("createdAt"), from, to);
            if (from != null) return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
            return cb.lessThanOrEqualTo(root.get("createdAt"), to);
        };
    }

    public static Specification<Ticket> searchText(String text) {
        return (root, query, cb) -> {
            if (text == null || text.isBlank()) return null;
            String pattern = "%" + text.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }

    public static Specification<Ticket> notDeleted() {
        return (root, query, cb) -> cb.equal(root.get("deleted"), false);
    }

    public static Specification<Ticket> hasQueueIn(List<Long> queueIds) {
    return (root, query, cb) ->
            (queueIds == null || queueIds.isEmpty())
                ? cb.disjunction()
                : root.get("queue").get("id").in(queueIds);
}
}