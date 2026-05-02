package org.example.ApplicationLayer;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.time.LocalDate;
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

    public void addOvertDiscount(UUID eventId ,LocalDate fromDate, LocalDate toDate, float discountPrecent)
    {
        if(toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if(discountPrecent > 100.0f || discountPrecent < 0.0f)
            throw new IllegalArgumentException("Discount precent must be between 0 and 100");
        eventManagementDomainService.addOvertDiscount(eventId, fromDate, toDate, discountPrecent);
    }

    public void addConditionalDiscount(UUID eventId ,LocalDate fromDate, LocalDate toDate, float discountPrecent, int requiredTickets, int appliedTickets)
    {
        if(toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if(discountPrecent > 100.0f || discountPrecent < 0.0f)
            throw new IllegalArgumentException("Discount precent must be between 0 and 100");
        if(requiredTickets < 0 )
            throw new IllegalArgumentException("Required tickets must be non negative integers");
        if(appliedTickets < 0 )
            throw new IllegalArgumentException("Applied tickets must be non negative integers");
        eventManagementDomainService.addConditionalDiscount(eventId, fromDate, toDate, discountPrecent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(UUID eventId, LocalDate fromDate, LocalDate toDate, float discountPrecent, String code)
    {
        if(toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if(discountPrecent > 100.0f || discountPrecent < 0.0f)
            throw new IllegalArgumentException("Discount precent must be between 0 and 100");
        eventManagementDomainService.addCouponCode(eventId, fromDate, toDate, discountPrecent, code);
    }
    
    public void removeDiscount(UUID eventId, UUID discountId)
    {
        eventManagementDomainService.removeDiscount(eventId, discountId);
    }

    public void rateEvent(UUID userID, UUID eventID, int rating)
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
