package com.tcc.servicedesk.ticket;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.time.Year;

@Component
public class TicketReferenceGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    public String generate() {
        Long nextValue = ((Number) entityManager
                .createNativeQuery("SELECT nextval('ticket_reference_seq')")
                .getSingleResult())
                .longValue();

        int currentYear = Year.now().getValue();

        return String.format("TKT-%d-%05d", currentYear, nextValue);
    }
}