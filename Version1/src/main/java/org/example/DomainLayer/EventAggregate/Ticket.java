package org.example.DomainLayer.EventAggregate;

/**
 * Abstract ticket in the event aggregate ({@link Event} 1:* {@link Ticket}).
 * Concrete kinds: {@link StandingTicket}, {@link SittingTicket}.
 */
public abstract class Ticket {

    private final int ticketId;
    private final int eventId;
    private final int areaId;
    private TicketStatus status;
    private final double price;

    protected Ticket(int ticketId, int eventId, int areaId, double price, TicketStatus initialStatus) {
        this.ticketId = ticketId;
        this.eventId = eventId;
        this.areaId = areaId;
        this.price = price;
        this.status = initialStatus;
    }

    public int getTicketId() {
        return ticketId;
    }

    public int getEventId() {
        return eventId;
    }

    public int getAreaId() {
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
