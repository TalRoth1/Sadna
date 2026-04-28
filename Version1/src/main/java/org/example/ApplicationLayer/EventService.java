package org.example.ApplicationLayer;

import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.util.List;
import java.util.Optional;
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

    public void addPolicyRule(UUID eventId, Optional<Float> age, Optional<Integer> minTicket, Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat)
    {
        if (age.isPresent() && age.get() < 0)
            throw new IllegalArgumentException("Age must be a non negative number");
        if (minTicket.isPresent() && minTicket.get() < 0)
            throw new IllegalArgumentException("Minimum ticket amount must be a non negative integer");
        if (maxTicket.isPresent() && maxTicket.get() < 0)
            throw new IllegalArgumentException("maximum ticket amount must be a non negative integer");
        eventManagementDomainService.addPurchasePolicy(eventId, age, minTicket, maxTicket, allowLoneSeat);
    }
    
    public void deletePolicyRule(UUID eventId, boolean age, boolean minTicket, boolean maxTicket, boolean allowLoneSeat)
    {
        eventManagementDomainService.deletePurchasePolicy(eventId, age, minTicket, maxTicket, allowLoneSeat);
    }
}
