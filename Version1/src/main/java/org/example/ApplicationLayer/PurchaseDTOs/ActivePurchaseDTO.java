package org.example.ApplicationLayer.PurchaseDTOs;

import java.time.LocalDateTime;
import java.util.List;
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

    // בנאי ריק חובה עבור סריאליזציה (Jackson)
    public ActivePurchaseDTO() {}
}
