package org.example.ApplicationLayer;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class EventService {
    private static final Logger logger = Logger.getLogger(EventService.class.getName());
    private final EventManagementDomainService eventManagementDomainService;

    public EventService(EventManagementDomainService eventManagementDomainService) {
        this.eventManagementDomainService = eventManagementDomainService;
    }
    
    public void addEvent(UUID eventId, UUID companyId, LocalDateTime date, String location,
                         String artist, String type, EventStatus status) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (companyId == null) {
            throw new IllegalArgumentException("companyId is required");
        }
        try {
            eventManagementDomainService.addEvent(eventId, companyId, date, location, artist, type, status);
            logger.info("Event added: " + eventId);
        } catch (RuntimeException e) {
            logger.severe("Error adding event " + eventId + ": " + e.getMessage());
            throw e;
        }
    }

    public boolean editEvent(UUID eventId, LocalDateTime date, String location,
                             String artist, String type, EventStatus status) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        try {
            boolean updated = eventManagementDomainService.editEvent(eventId, date, location, artist, type, status);
            logger.info("Event edited: " + eventId);
            return updated;
        } catch (RuntimeException e) {
            logger.severe("Error editing event " + eventId + ": " + e.getMessage());
            throw e;
        }
    }

    public boolean deleteEvent(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        try {
            boolean deleted = eventManagementDomainService.deleteEvent(eventId);
            logger.info("Event deleted: " + eventId);
            return deleted;
        } catch (RuntimeException e) {
            logger.severe("Error deleting event " + eventId + ": " + e.getMessage());
            throw e;
        }
    }

    public void addStandingTickets(UUID eventId, UUID areaId, int count) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (areaId == null) {
            throw new IllegalArgumentException("areaId is required");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        try {
            eventManagementDomainService.addStandingTickets(eventId, areaId, count);
            logger.info("Added " + count + " standing tickets to area " + areaId + " of event " + eventId);
        } catch (RuntimeException e) {
            logger.severe("Error adding standing tickets to event " + eventId + ": " + e.getMessage());
            throw e;
        }
    }

    public void addSittingTickets(UUID eventId, UUID areaId, int rows, int seatsPerRow) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (areaId == null) {
            throw new IllegalArgumentException("areaId is required");
        }
        if (rows <= 0 || seatsPerRow <= 0) {
            throw new IllegalArgumentException("rows and seatsPerRow must be positive");
        }
        try {
            eventManagementDomainService.addSittingTickets(eventId, areaId, rows, seatsPerRow);
            logger.info("Added " + (rows * seatsPerRow) + " sitting tickets to area " + areaId + " of event " + eventId);
        } catch (RuntimeException e) {
            logger.severe("Error adding sitting tickets to event " + eventId + ": " + e.getMessage());
            throw e;
        }
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
