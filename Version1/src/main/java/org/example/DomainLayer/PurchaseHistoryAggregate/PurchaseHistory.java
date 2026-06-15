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
    // Confirmation/secure code returned by the external ticketing system at
    // issuance time. Immutable: set once at construction. May be null for
    // historical records created before issuance tracking existed.
    private final String issuedTicketReference;

    public PurchaseHistory(UUID userId, List<UUID> ticketIds, UUID eventId, Payment payment) {
        this(userId, ticketIds, eventId, payment, LocalDateTime.now(), null);
    }

    public PurchaseHistory(UUID userId, List<UUID> ticketIds, UUID eventId, Payment payment, LocalDateTime purchaseDate) {
        this(userId, ticketIds, eventId, payment, purchaseDate, null);
    }

    public PurchaseHistory(UUID userId, List<UUID> ticketIds, UUID eventId, Payment payment,
                           String issuedTicketReference) {
        this(userId, ticketIds, eventId, payment, LocalDateTime.now(), issuedTicketReference);
    }

    public PurchaseHistory(UUID userId, List<UUID> ticketIds, UUID eventId, Payment payment,
                           LocalDateTime purchaseDate, String issuedTicketReference) {
        this.userId = userId;
        this.ticketIds = new ArrayList<>(ticketIds);
        this.eventId = eventId;
        this.payment = payment;
        this.purchaseDate = purchaseDate;
        this.issuedTicketReference = issuedTicketReference;
    }

    public UUID getUserId() {
        return userId;
    }

    public List<UUID> getTicketIds() {
        return new ArrayList<>(ticketIds);
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

    public String getIssuedTicketReference() {
        return issuedTicketReference;
    }
}
