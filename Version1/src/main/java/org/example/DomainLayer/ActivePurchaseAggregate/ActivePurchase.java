package org.example.DomainLayer.ActivePurchaseAggregate;

import java.time.LocalTime;
import java.util.List;

public class ActivePurchase
{
    private String userID;
    private List<String> ticketIDs;
    private String eventID;
    private LocalTime endTime;


    public ActivePurchase(String userID, String eventID, List<String> ticketIDs, LocalTime endTime)
    {
        this.userID = userID;
        this.ticketIDs = ticketIDs;
        this.eventID = eventID;
        this.endTime = endTime;
    }
}
