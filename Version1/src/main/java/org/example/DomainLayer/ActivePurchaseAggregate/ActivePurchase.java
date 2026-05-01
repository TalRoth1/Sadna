package org.example.DomainLayer.ActivePurchaseAggregate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public class ActivePurchase
{
    private UUID id;
    private UUID userID;
    private Map<UUID, Float> ticketIDPrices;
    private UUID eventID;
    private LocalDateTime endTime;
    private boolean isGuestConfirmedAge = false;
    private String coupon;
    private float price;


    public ActivePurchase(UUID userID, UUID eventID, Map<UUID, Float> ticketIDPrices, LocalDateTime endTime)
    {
        this.id = UUID.randomUUID();
        this.userID = userID;
        this.ticketIDPrices = ticketIDPrices;
        this.eventID = eventID;
        this.endTime = endTime;
        this.coupon = "";
        this.price = ticketIDPrices.values().stream().reduce(0.0f, Float::sum);
    }

    public void SetGuestAgeConfirmed(boolean isGuestConfirmedAge)
    {
        this.isGuestConfirmedAge = isGuestConfirmedAge;
    }
    public UUID getUserID()
    {
        return this.userID;
    }

    public Map<UUID, Float> getTicketIDs()
    {
        return Map.copyOf(this.ticketIDPrices);
    }

    public UUID getEventID()
    {
        return this.eventID;
    }

    public LocalDateTime getEndTime()
    {
        return this.endTime;
    }

    public float getPrice()
    {
        return price;
    }

    public void setPrice(float price)
    {
        this.price = price;
    }

    public String getCoupon() {
        return coupon;
    }

    public void setCoupon(String couponCode)
    {
        this.coupon = couponCode;
    }

    public boolean getGuestAgeConfirmed()
    {
        return this.isGuestConfirmedAge;
    }
    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(endTime);
    }
    public UUID getActivePurchaseId()
    {
        return this.id;
    }
    public void setNewTicketPricePrice(UUID ticketID, float newPrice)
    {
        ticketIDPrices.put(ticketID, newPrice);
    }

    public float getCurrentPrice(UUID ticketId)
    {
        return ticketIDPrices.get(ticketId);
    }
}
