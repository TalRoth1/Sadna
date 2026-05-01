package org.example.DomainLayer.EventAggregate;

import java.util.UUID;

/**
 * Seated zone (assigned seats; individual seats are modeled as {@link SittingTicket}s).
 */
public class SittingArea extends Area {

    public SittingArea(UUID areaId, double price) {
        super(areaId, price);
    }

    public SittingTicket getTicketAt(int row, int col, Event event) {
        return getTicketIdsView().stream()
            .map(event::getTicket)
            .filter(t -> t instanceof SittingTicket)
            .map(t -> (SittingTicket) t)
            .filter(st -> st.getSeatRow() == row && st.getSeatNumber() == col)
            .findFirst()
            .orElse(null);
    }
}
