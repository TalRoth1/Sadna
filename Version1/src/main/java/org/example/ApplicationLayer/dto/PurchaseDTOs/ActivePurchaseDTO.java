package org.example.ApplicationLayer.dto.PurchaseDTOs;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class ActivePurchaseDTO {
    public UUID activePurchaseId;
    public UUID userId;
    public UUID eventId;
    public Map<UUID, Float> ticketPrices; // מייצג את ticketIDPrices מהדומיין
    public LocalDateTime endTime;
    public boolean isGuestConfirmedAge;
    public String coupon;
    public float price;
    public float maxWaitTime;
    public LocalDateTime lastUpdate;

    // Denormalized event fields so the ticket-purchase / checkout screens
    // don't need to fire a follow-up request just to render the event header.
    public String eventName;
    public LocalDateTime eventDate;
    public String eventLocation;
    public int ticketsAmount;

    // Required empty constructor for serialization (Jackson)
    public ActivePurchaseDTO() {}
}
