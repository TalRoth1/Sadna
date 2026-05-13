package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.EventDtos.AreaSummaryDto;
import org.example.ApplicationLayer.dto.EventDtos.CompanyCatalogDto;
import org.example.ApplicationLayer.dto.EventDtos.EventDetailsDto;
import org.example.ApplicationLayer.dto.EventDtos.EventSummaryDto;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.Area;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventSearchCriteria;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.PolicyManagment.IDiscountRule;
import org.example.DomainLayer.PolicyManagment.IPurchaseRule;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    public void addPolicyRule(String username, UUID companyId, UUID eventId, Optional<Float> age, Optional<Integer> minTicket, Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat) {
        logger.info("User '" + username + "' attempting to add/update policy rules for Event ID: " + eventId + " (Company ID: " + companyId + ")");

        try {
            if (age.isPresent() && age.get() < 0) {
                logger.warning("Event policy addition failed: Invalid age (" + age.get() + ") provided by user: " + username);
                throw new IllegalArgumentException("Age must be a non negative number");
            }
            if (minTicket.isPresent() && minTicket.get() < 0) {
                logger.warning("Event policy addition failed: Invalid minTicket (" + minTicket.get() + ") provided by user: " + username);
                throw new IllegalArgumentException("Minimum ticket amount must be a non negative integer");
            }
            if (maxTicket.isPresent() && maxTicket.get() < 0) {
                logger.warning("Event policy addition failed: Invalid maxTicket (" + maxTicket.get() + ") provided by user: " + username);
                throw new IllegalArgumentException("maximum ticket amount must be a non negative integer");
            }

            eventManagementDomainService.addPurchasePolicy(username, companyId, eventId, age, minTicket, maxTicket, allowLoneSeat);
            
            logger.info("Successfully updated policy rules for Event ID: " + eventId + " by user: " + username);

        } catch (Exception e) {
            logger.severe("Unexpected error adding policy rule for Event ID: " + eventId + ". Error: " + e.getMessage());
            throw e;
        }
    }

    public void deletePolicyRule(String username, UUID companyId, UUID eventId, boolean age, boolean minTicket, boolean maxTicket, boolean allowLoneSeat) {
        logger.info("User '" + username + "' attempting to delete specific policy rules for Event ID: " + eventId + " (Company ID: " + companyId + ")");

        try {
            eventManagementDomainService.deletePurchasePolicy(username, companyId, eventId, age, minTicket, maxTicket, allowLoneSeat);
            
            logger.info("Successfully deleted requested policy rules for Event ID: " + eventId + " by user: " + username);
            
        } catch (Exception e) {
            logger.severe("Failed to delete policy rules for Event ID: " + eventId + " by user: " + username + ". Error: " + e.getMessage());
            throw e;
        }
    }

    public void addOvertDiscount(String username, UUID companyId, UUID eventId ,LocalDate fromDate, LocalDate toDate, float discountPrecent)
    {
        if(toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if(discountPrecent > 100.0f || discountPrecent < 0.0f)
            throw new IllegalArgumentException("Discount precent must be between 0 and 100");
        eventManagementDomainService.addOvertDiscount(username, companyId, eventId, fromDate, toDate, discountPrecent);
    }

    public void addConditionalDiscount(String username, UUID comapnyId, UUID eventId ,LocalDate fromDate, LocalDate toDate, float discountPrecent, int requiredTickets, int appliedTickets)
    {
        if(toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if(discountPrecent > 100.0f || discountPrecent < 0.0f)
            throw new IllegalArgumentException("Discount precent must be between 0 and 100");
        if(requiredTickets < 0 )
            throw new IllegalArgumentException("Required tickets must be non negative integers");
        if(appliedTickets < 0 )
            throw new IllegalArgumentException("Applied tickets must be non negative integers");
        eventManagementDomainService.addConditionalDiscount(username, comapnyId, eventId, fromDate, toDate, discountPrecent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(String username, UUID companyId,UUID eventId, LocalDate fromDate, LocalDate toDate, float discountPrecent, String code)
    {
        if(toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if(discountPrecent > 100.0f || discountPrecent < 0.0f)
            throw new IllegalArgumentException("Discount precent must be between 0 and 100");
        eventManagementDomainService.addCouponCode(username, companyId, eventId, fromDate, toDate, discountPrecent, code);
    }
    
    public void removeDiscount(String username, UUID compnayId, UUID eventId, UUID discountId)
    {
        eventManagementDomainService.removeDiscount(username, compnayId, eventId, discountId);
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
            throw e;
        }
    }

    public List<CompanyCatalogDto> browseCatalog() {
        logger.info("browseCatalog requested");
        try {
            List<Company> active = eventManagementDomainService.getActiveCompanies();
            List<CompanyCatalogDto> out = new ArrayList<>();
            for (Company c : active) {
                List<Event> events = eventManagementDomainService.getVisibleEventsForCompany(c.getId());
                List<EventSummaryDto> summaries = new ArrayList<>();
                for (Event e : events) {
                    summaries.add(toSummary(e));
                }
                out.add(new CompanyCatalogDto(c.getId(), c.getName(), c.getRating(), summaries));
            }
            if (out.isEmpty()) {
                logger.info("browseCatalog returned no active companies");
            }
            return out;
        } catch (RuntimeException e) {
            logger.severe("browseCatalog failed: " + e.getMessage());
            throw e;
        }
    }

    public EventDetailsDto getEventDetails(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        logger.info("getEventDetails requested for event " + eventId);
        try {
            Event e = eventManagementDomainService.getEventForView(eventId);
            return toDetails(e);
        } catch (RuntimeException ex) {
            logger.severe("getEventDetails failed for event " + eventId + ": " + ex.getMessage());
            throw ex;
        }
    }

    public List<EventSummaryDto> searchEvents(EventSearchCriteria criteria) {
        EventSearchCriteria c = (criteria == null) ? EventSearchCriteria.empty() : criteria;
        validateCriteria(c);
        logger.info("searchEvents requested with criteria: " + c);
        try {
            List<Event> matches = eventManagementDomainService.searchEvents(c);
            List<EventSummaryDto> out = new ArrayList<>();
            for (Event e : matches) {
                out.add(toSummary(e));
            }
            if (out.isEmpty()) {
                logger.info("searchEvents returned no matches");
            }
            return out;
        } catch (RuntimeException ex) {
            logger.severe("searchEvents failed: " + ex.getMessage());
            throw ex;
        }
    }

    public List<EventSummaryDto> searchEventsByCompany(UUID companyId, EventSearchCriteria criteria) {
        if (companyId == null) {
            throw new IllegalArgumentException("companyId is required");
        }
        EventSearchCriteria c = (criteria == null) ? EventSearchCriteria.empty() : criteria;
        EventSearchCriteria scoped = c.withCompanyId(companyId);
        validateCriteria(scoped);
        logger.info("searchEventsByCompany requested for company " + companyId + " with criteria: " + scoped);
        try {
            List<Event> matches = eventManagementDomainService.searchEvents(scoped);
            List<EventSummaryDto> out = new ArrayList<>();
            for (Event e : matches) {
                out.add(toSummary(e));
            }
            if (out.isEmpty()) {
                logger.info("searchEventsByCompany returned no matches for company " + companyId);
            }
            return out;
        } catch (RuntimeException ex) {
            logger.severe("searchEventsByCompany failed for company " + companyId + ": " + ex.getMessage());
            throw ex;
        }
    }

    private static void validateCriteria(EventSearchCriteria c) {
        if (c.priceMin().isPresent() && c.priceMin().get() < 0) {
            throw new IllegalArgumentException("priceMin must be non-negative");
        }
        if (c.priceMax().isPresent() && c.priceMax().get() < 0) {
            throw new IllegalArgumentException("priceMax must be non-negative");
        }
        if (c.priceMin().isPresent() && c.priceMax().isPresent()
                && c.priceMin().get() > c.priceMax().get()) {
            throw new IllegalArgumentException("priceMin must be <= priceMax");
        }
        if (c.dateFrom().isPresent() && c.dateTo().isPresent()
                && c.dateFrom().get().isAfter(c.dateTo().get())) {
            throw new IllegalArgumentException("dateFrom must be <= dateTo");
        }
        if (c.minEventRating().isPresent()
                && (c.minEventRating().get() < 0 || c.minEventRating().get() > 5)) {
            throw new IllegalArgumentException("minEventRating must be in [0,5]");
        }
        if (c.minCompanyRating().isPresent()
                && (c.minCompanyRating().get() < 0 || c.minCompanyRating().get() > 5)) {
            throw new IllegalArgumentException("minCompanyRating must be in [0,5]");
        }
    }

    private static EventSummaryDto toSummary(Event e) {
        return new EventSummaryDto(
                e.getEventId(),
                e.getCompanyId(),
                e.getName(),
                e.getArtist(),
                e.getType(),
                e.getDate(),
                e.getLocation(),
                e.getRating());
    }

    private static EventDetailsDto toDetails(Event e) {
        List<AreaSummaryDto> areas = new ArrayList<>();
        for (Area a : e.getLayout().getAreasView()) {
            String kind = (a instanceof StandingArea) ? "STANDING"
                    : (a instanceof SittingArea) ? "SITTING" : "UNKNOWN";
            areas.add(new AreaSummaryDto(a.getAreaId(), kind, a.getPrice()));
        }
        List<String> purchaseRules = new ArrayList<>();
        for (IPurchaseRule r : e.getPurchasePolicy().getRulesView()) {
            purchaseRules.add(r.getClass().getSimpleName());
        }
        List<String> discountRules = new ArrayList<>();
        for (IDiscountRule r : e.getDiscountPolicy().gDiscountRules()) {
            discountRules.add(r.getClass().getSimpleName());
        }
        return new EventDetailsDto(
                e.getEventId(),
                e.getCompanyId(),
                e.getName(),
                e.getArtist(),
                e.getType(),
                e.getDate(),
                e.getLocation(),
                e.getRating(),
                areas,
                purchaseRules,
                discountRules);
    }
}
