package com.tcc.servicedesk.ticket;

import java.util.Map;
import java.util.Set;

public final class TicketStatusTransitions {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED =
            Map.of(
                    TicketStatus.NEW, Set.of(TicketStatus.ASSIGNED),
                    TicketStatus.ASSIGNED, Set.of(TicketStatus.IN_PROGRESS),
                    TicketStatus.IN_PROGRESS,
                            Set.of(TicketStatus.PENDING_REQUESTER, TicketStatus.RESOLVED),
                    TicketStatus.PENDING_REQUESTER,
                            Set.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED),
                    TicketStatus.RESOLVED, Set.of(TicketStatus.CLOSED, TicketStatus.REOPENED),
                    TicketStatus.CLOSED, Set.of(TicketStatus.REOPENED),
                    TicketStatus.REOPENED, Set.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS));

    private TicketStatusTransitions() {}

    public static boolean isValid(TicketStatus from, TicketStatus to) {
        Set<TicketStatus> allowedTargets = ALLOWED.get(from);
        return allowedTargets != null && allowedTargets.contains(to);
    }
}
