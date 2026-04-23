package org.example.DomainLayer.ActivePurchaseAggregate;

import java.time.LocalDateTime;
import java.util.*;

public class ActivePurchase
{
    private String userID;
    private LinkedHashMap<Integer, Double> ticketsCurrentPrices;
    private int eventID;
    private LocalDateTime endTime;
    private boolean isGuestConfirmedAge = false;

    private final String activePurchaseId;


    public ActivePurchase(String userID, int eventID, LinkedHashMap<Integer, Double> ticketBasePrices, LocalDateTime endTime)
    {
        this.userID = userID;
        this.ticketsCurrentPrices = ticketBasePrices;
        this.eventID = eventID;
        this.endTime = endTime;

        this.activePurchaseId = UUID.randomUUID().toString();
    }
    public void SetGuestAgeConfirmed(boolean isGuestConfirmedAge)
    {
        this.isGuestConfirmedAge = isGuestConfirmedAge;
    }

    public String getUserID()
    {
        return this.userID;
    }

    public List<Integer> getTicketIDs()
    {
        return new ArrayList<>(ticketsCurrentPrices.keySet());
    }
    public Map<Integer, Double> getTicketsCurrentPrices() {
        return Map.copyOf(ticketsCurrentPrices);
    }

    public int getEventID()
    {
        return this.eventID;
    }

    public LocalDateTime getEndTime()
    {
        return this.endTime;
    }
    public boolean getGuestAgeConfirmed()
    {
        return this.isGuestConfirmedAge;
    }
    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(endTime);
    }
    public String getActivePurchaseId()
    {
        return this.activePurchaseId;
    }
    public void setNewPrice(int ticketID, double newPrice)
    {
        ticketsCurrentPrices.put(ticketID, newPrice);
    }

    public double getCurrentPrice(int ticketId)
    {
        return ticketsCurrentPrices.get(ticketId);
    }
    public double calculateCurrentTotalPrice()
    {
        double total = 0.0;
        for (double price : ticketsCurrentPrices.values()) {
            total += price;
        }
        return total;
    }
}
