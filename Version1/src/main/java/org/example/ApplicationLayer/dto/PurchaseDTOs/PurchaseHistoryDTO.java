package org.example.ApplicationLayer.dto.PurchaseDTOs;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PurchaseHistoryDTO {
    public UUID userId;
    public UUID eventId;
    public List<UUID> ticketIds;

    // Denormalized event fields so the frontend can render and filter the
    // purchase-history page without firing N+1 queries to fetch each event.
    public String eventName;
    public LocalDateTime eventDate;
    public String eventLocation;
    public int ticketsAmount;
    // Note: field name matches the frontend JSON key (`totalPrice`).
    public double totalPrice;
    public String paymentInfo;     // Added to display the payment method (e.g. "Visa ****1234")

    public LocalDateTime purchaseDate;

    public PurchaseHistoryDTO() {}
}
