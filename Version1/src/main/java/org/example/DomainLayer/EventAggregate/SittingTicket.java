package org.example.DomainLayer.EventAggregate;

/**
 * Seated ticket with a seat identifier ({@code SeatNumber} in the domain model).
 */
public class SittingTicket extends Ticket {

    private final int seatNumber;
    private final int seatRow;

    public SittingTicket(int ticketId, int eventId, int areaId, double price, int seatNumber, int seatRow) {
        super(ticketId, eventId, areaId, price, TicketStatus.AVAILABLE);

        /*
            if (seatNumber == null || seatNumber.isBlank()) {
            throw new IllegalArgumentException("seatNumber required");
        }
        this.seatNumber = seatNumber.trim();
         */
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
