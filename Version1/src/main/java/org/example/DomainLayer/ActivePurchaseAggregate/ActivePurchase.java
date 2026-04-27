package org.example.DomainLayer.ActivePurchaseAggregate;

import java.time.LocalTime;
import java.util.Map;

public class ActivePurchase
{
    private String userID;
    private Map<Integer, Float> ticketIDPrices;
    private int eventID;
    private LocalTime endTime;
    private String coupon;
    private float price;


    public ActivePurchase(String userID, int eventID, Map<Integer, Float> ticketIDPrices, LocalTime endTime)
    {
        this.userID = userID;
        this.ticketIDPrices = ticketIDPrices;
        this.eventID = eventID;
        this.endTime = endTime;
        this.coupon = "";
        this.price = ticketIDPrices.values().stream().reduce(0.0f, Float::sum);
    }

    public String getUserID()
    {
        return this.userID;
    }

    public Map<Integer, Float> getTicketIDs()
    {
        return this.ticketIDPrices;
    }

    public int getEventID()
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
