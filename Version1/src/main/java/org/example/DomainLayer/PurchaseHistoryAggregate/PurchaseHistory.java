package org.example.DomainLayer.PurchaseHistoryAggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PurchaseHistory {
    private final int userId;
    private final List<Integer> ticketIds;
    private final int eventId;
    private final Payment payment;
    private final LocalDateTime purchaseDate;

    public PurchaseHistory(int userId, List<Integer> ticketIds, int eventId, Payment payment) {
        this.userId = userId;
        this.ticketIds = new ArrayList<>(ticketIds);
        this.eventId = eventId;
        this.payment = payment;
        this.purchaseDate = LocalDateTime.now();
    }

    public int getUserId() {
        return userId;
    }

    public List<Integer> getTicketIds() {
        return new ArrayList<>(ticketIds);    }

    public int getEventId() {
        return eventId;
    }

    public Payment getPayment() {
        return payment;
    }

    public LocalDateTime getPurchaseDate() {
        return purchaseDate;
    }
}
