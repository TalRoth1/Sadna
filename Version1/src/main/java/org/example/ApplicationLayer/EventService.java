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
}
