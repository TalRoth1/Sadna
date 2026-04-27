package org.example.DomainLayer.EventAggregate;

import java.util.UUID;

/**
 * Standing-area ticket (no assigned seat).
 */
public class StandingTicket extends Ticket {

    public StandingTicket(UUID ticketId, UUID eventId, UUID areaId, double price) {
        super(ticketId, eventId, areaId, price, TicketStatus.AVAILABLE);
    }
}
