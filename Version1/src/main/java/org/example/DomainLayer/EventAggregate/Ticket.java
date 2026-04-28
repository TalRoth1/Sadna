package org.example.DomainLayer.EventAggregate;

import java.util.UUID;

/**
 * Abstract ticket in the event aggregate ({@link Event} 1:* {@link Ticket}).
 * Concrete kinds: {@link StandingTicket}, {@link SittingTicket}.
 */
public abstract class Ticket {

    private final UUID ticketId;
    private final UUID eventId;
    private final UUID areaId;
    private TicketStatus status;
    private final double price;

    protected Ticket(UUID ticketId, UUID eventId, UUID areaId, double price, TicketStatus initialStatus) {
        this.ticketId = ticketId;
        this.eventId = eventId;
        this.areaId = areaId;
        this.price = price;
        this.status = initialStatus;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public double getPrice() {
        return price;
    }

    public void reserve() {
        if (status != TicketStatus.AVAILABLE) {
            throw new IllegalStateException("ticket not available: " + ticketId);
        }
        status = TicketStatus.RESERVED;
    }

    public void releaseReservation() {
        if (status != TicketStatus.RESERVED) {
            throw new IllegalStateException("ticket not reserved: " + ticketId);
        }
        status = TicketStatus.AVAILABLE;
    }

    public void markSold() {
        if (status != TicketStatus.RESERVED) {
            throw new IllegalStateException("cannot complete purchase from state " + status + ": " + ticketId);
        }
        status = TicketStatus.SOLD;
    }
}
