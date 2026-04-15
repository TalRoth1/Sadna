package org.example.DomainLayer.EventAggregate;

/**
 * Standing-area ticket (no assigned seat).
 */
public class StandingTicket extends Ticket {

    public StandingTicket(String ticketId, String eventId, String areaId, double price) {
        super(ticketId, eventId, areaId, price, TicketStatus.AVAILABLE);
    }
}
