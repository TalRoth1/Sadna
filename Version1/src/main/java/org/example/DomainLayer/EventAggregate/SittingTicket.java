package org.example.DomainLayer.EventAggregate;

import java.util.UUID;

/**
 * Seated ticket with a seat identifier ({@code SeatNumber} in the domain model).
 */
public class SittingTicket extends Ticket {

    private final String seatNumber;

    public SittingTicket(UUID ticketId, UUID eventId, UUID areaId, double price, String seatNumber) {
        super(ticketId, eventId, areaId, price, TicketStatus.AVAILABLE);
        if (seatNumber == null || seatNumber.isBlank()) {
            throw new IllegalArgumentException("seatNumber required");
        }
        this.seatNumber = seatNumber.trim();
    }

    public String getSeatNumber() {
        return seatNumber;
    }
}
