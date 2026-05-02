package org.example.DomainLayer.PurchaseHistoryAggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PurchaseHistory {
    private final UUID userId;
    private final List<UUID> ticketIds;
    private final UUID eventId;
    private final Payment payment;
    private final LocalDateTime purchaseDate;

    public PurchaseHistory(UUID userId, List<UUID> ticketIds, UUID eventId, Payment payment) {
        this.userId = userId;
        this.ticketIds = new ArrayList<>(ticketIds);
        this.eventId = eventId;
        this.payment = payment;
        this.purchaseDate = LocalDateTime.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public List<UUID> getTicketIds() {
        return ticketIds;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Payment getPayment() {
        return payment;
    }

    public LocalDateTime getPurchaseDate() {
        return purchaseDate;
    }
}
