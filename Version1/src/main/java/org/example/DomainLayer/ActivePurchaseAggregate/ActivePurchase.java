package org.example.DomainLayer.ActivePurchaseAggregate;

import java.time.LocalTime;
import java.util.List;

public class ActivePurchase
{
    private String userID;
    private List<Integer> ticketIDs;
    private int eventID;
    private LocalTime endTime;


    public ActivePurchase(String userID, int eventID, List<Integer> ticketIDs, LocalTime endTime)
    {
        this.userID = userID;
        this.ticketIDs = ticketIDs;
        this.eventID = eventID;
        this.endTime = endTime;
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

    public LocalTime getEndTime()
    {
        return this.endTime;
    }
} 
