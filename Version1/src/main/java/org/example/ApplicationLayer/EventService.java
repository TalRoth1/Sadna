package org.example.ApplicationLayer;

import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.util.List;
import java.util.UUID;

public class EventService {
    private final EventManagementDomainService eventManagementDomainService;

    public EventService(EventManagementDomainService eventManagementDomainService) {
        this.eventManagementDomainService = eventManagementDomainService;
    }

    public List<PurchaseHistory> getEventPurchaseHistoryForOwner(String ownerUsername, UUID eventId) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        return eventManagementDomainService.getEventPurchaseHistory(ownerUsername, eventId);
    }

    public void addAgePolicy(UUID eventId,float age)
    {
        if (age < 0)
            throw new IllegalArgumentException("Age must be a non negative number");
        eventManagementDomainService.addAgePolicy(eventId, age);
    }

    public void deleteAgePolicy(UUID eventId)
    {
        eventManagementDomainService.deleteAgePolicy(eventId);
    }

    public void addMinTicketPolicy(UUID eventId,int minTicket)
    {
        if (minTicket < 0)
            throw new IllegalArgumentException("Minimum ticket amount must be a non negative integer");
        eventManagementDomainService.addMinTicketPolicy(eventId, minTicket);
    }

    public void deleteMinTicketPolicy(UUID eventId)
    {
        eventManagementDomainService.deleteMinTicketPolicy(eventId);
    }

    public void addMaxTicketPolicy(UUID eventId,int maxTicket)
    {
        if (maxTicket < 0)
            throw new IllegalArgumentException("maximum ticket amount must be a non negative integer");
        eventManagementDomainService.addMaxTicketPolicy(eventId, maxTicket);
    }

    public void deleteMaxTicketPolicy(UUID eventId)
    {
        eventManagementDomainService.deleteMaxTicketPolicy(eventId);
    }

    public void addLoneSeatPolicy(UUID eventId, boolean allowLoneSeat)
    {
        eventManagementDomainService.addLoneSeatPolicy(eventId, allowLoneSeat);
    }

    public void deleteLoneSeatPolicy(UUID eventId)
    {
        eventManagementDomainService.deleteLoneSeatPolicy(eventId);
    }
}
