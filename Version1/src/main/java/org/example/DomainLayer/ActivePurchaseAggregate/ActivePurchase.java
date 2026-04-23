package org.example.DomainLayer.ActivePurchaseAggregate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ActivePurchase
{
    private String userID;
    private List<Integer> ticketIDs;
    private int eventID;
    private LocalDateTime endTime;
    private boolean isGuestConfirmedAge = false;

    private final String activePurchaseId;


    public ActivePurchase(String userID, int eventID, List<Integer> ticketIDs, LocalDateTime endTime)
    {
        this.userID = userID;
        this.ticketIDs = ticketIDs;
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
        return this.ticketIDs;
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
} 
