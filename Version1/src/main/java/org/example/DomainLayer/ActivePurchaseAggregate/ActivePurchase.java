package org.example.DomainLayer.ActivePurchaseAggregate;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

public class ActivePurchase
{
    private UUID userID;
    private Map<UUID, Float> ticketIDPrices;
    private UUID eventID;
    private LocalTime endTime;
    private String coupon;
    private float price;


    public ActivePurchase(UUID userID, UUID eventID, Map<UUID, Float> ticketIDPrices, LocalTime endTime)
    {
        this.userID = userID;
        this.ticketIDPrices = ticketIDPrices;
        this.eventID = eventID;
        this.endTime = endTime;
        this.coupon = "";
        this.price = ticketIDPrices.values().stream().reduce(0.0f, Float::sum);
    }

    public UUID getUserID()
    {
        return this.userID;
    }

    public Map<UUID, Float> getTicketIDs()
    {
        return this.ticketIDPrices;
    }

    public UUID getEventID()
    {
        return this.eventID;
    }

    public LocalTime getEndTime()
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
} 
