package org.example.ApplicationLayer;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventManagementDomainService;

public class EventService
{
    EventManagementDomainService eventManagementDomainService;

    public void rateEvent(String userID, int eventID, int rating)
    {
        if (rating < 0 || rating > 5)
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        try
        {
            eventManagementDomainService.rateEvent(userID, eventID, rating);
        }
        catch (DomainException e)
        {
            //TODO
        }
    }
}
