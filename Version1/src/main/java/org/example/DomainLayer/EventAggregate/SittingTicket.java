package org.example.DomainLayer.EventAggregate;

import java.util.UUID;

public class SittingTicket extends Ticket {

    private final int seatNumber;
    private final int seatRow;

    public SittingTicket(UUID ticketId, UUID eventId, UUID areaId, float price, int seatNumber, int seatRow) {
        super(ticketId, eventId, areaId, price, TicketStatus.AVAILABLE);
        this.seatNumber = seatNumber;
        this.seatRow = seatRow;
    }

    public int getSeatNumber() {
        return seatNumber;
    }
    public int getSeatRow() {
        return seatRow;
    }

}
