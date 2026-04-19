package org.example.DomainLayer.EventAggregate;

/**
 * Standing-area ticket (no assigned seat).
 */
public class StandingTicket extends Ticket {

    public StandingTicket(int ticketId, int eventId, int areaId, double price) {
        super(ticketId, eventId, areaId, price, TicketStatus.AVAILABLE);
    }
}
