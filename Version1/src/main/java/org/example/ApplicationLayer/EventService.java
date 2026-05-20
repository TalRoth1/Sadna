package org.example.ApplicationLayer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.AreaSummaryDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.CompanyCatalogDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.EventDetailsDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.EventSummaryDto;
import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;
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
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * EventService
 *
 * Returns payload DTOs (EventDetailsDto, EventSummaryDto, etc.) — never Domain objects.
 * Controller wraps them in ApiResponse<T>.
 *
 * All Domain → DTO mapping is centralized in the private mapper methods.
 */

public class EventService {
    private static final Logger logger = Logger.getLogger(EventService.class.getName());
    private final EventManagementDomainService eventManagementDomainService;

    public EventService(EventManagementDomainService eventManagementDomainService) {
        this.eventManagementDomainService = eventManagementDomainService;
    }



    // ================================================================
    //  Event lifecycle
    // ================================================================

    public EventDetailsDto addEvent(UUID eventId, UUID companyId, String name,
                                    LocalDateTime date, String location,
                                    String artist, String type, EventStatus status) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (companyId == null) {
            throw new IllegalArgumentException("companyId is required");
        }
        try {
            Event created = eventManagementDomainService.addEvent(
                    eventId, companyId, name, date, location, artist, type, status);
            logger.info("Event added: " + eventId);
            return toDetails(created);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in addEvent: " + e.getMessage(), e);
            throw e;
        }
    }

    public EventSummaryDto editEvent(UUID eventId, LocalDateTime date, String location,
                                     String artist, String type, EventStatus status) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        try {
            Event updated = eventManagementDomainService.editEvent(
                    eventId, date, location, artist, type, status);
            logger.info("Event edited: " + eventId);
            return toSummary(updated);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in editEvent: " + e.getMessage(), e);
            throw e;
        }
    }

    public void deleteEvent(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        try {
            eventManagementDomainService.deleteEvent(eventId);
            logger.info("Event deleted: " + eventId);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in deleteEvent: " + e.getMessage(), e);
            throw e;
        }
    }

    // ================================================================
    //  Layout & tickets
    // ================================================================

    public void addStandingTickets(UUID eventId, UUID areaId, int count) {

        logger.info("[Event Log] Method: addStandingTickets called with parameters: eventId=" + eventId
                + ", areaId=" + areaId + ", count=" + count);

        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (areaId == null) throw new IllegalArgumentException("areaId is required");
        if (count <= 0) throw new IllegalArgumentException("count must be positive");

        try {
            if (eventId == null) {
                throw new IllegalArgumentException("eventId is required");
            }
            if (areaId == null) {
                throw new IllegalArgumentException("areaId is required");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive");
            }
            eventManagementDomainService.addStandingTickets(eventId, areaId, count);
        } catch (IllegalArgumentException | DomainException e) {
            logger.info("[Event Log] Business rejection in addStandingTickets: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in addStandingTickets: " + e.getMessage(), e);
            throw e;
        }
    }

    public void addSittingTickets(UUID eventId, UUID areaId, int rows, int seatsPerRow) {

        logger.info("[Event Log] Method: addSittingTickets called with parameters: eventId=" + eventId
                + ", areaId=" + areaId + ", rows=" + rows + ", seatsPerRow=" + seatsPerRow);

        if (eventId == null) throw new IllegalArgumentException("eventId is required");
        if (areaId == null) throw new IllegalArgumentException("areaId is required");
        if (rows <= 0 || seatsPerRow <= 0)
            throw new IllegalArgumentException("rows and seatsPerRow must be positive");
        try {
            if (eventId == null) {
                throw new IllegalArgumentException("eventId is required");
            }
            if (areaId == null) {
                throw new IllegalArgumentException("areaId is required");
            }
            if (rows <= 0 || seatsPerRow <= 0) {
                throw new IllegalArgumentException("rows and seatsPerRow must be positive");
            }
            eventManagementDomainService.addSittingTickets(eventId, areaId, rows, seatsPerRow);
        } catch (IllegalArgumentException | DomainException e) {
            logger.info("[Event Log] Business rejection in addSittingTickets: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in addSittingTickets: " + e.getMessage(), e);
            throw e;
        }
    }

    public void addPolicyRule(String username, UUID companyId, UUID eventId, Optional<Float> age, Optional<Integer> minTicket, Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat, boolean andOr)
    {
        logger.info("[Event Log] Method: addPolicyRule called with parameters: username=" + username
                + ", companyId=" + companyId + ", eventId=" + eventId + ", age=" + age
                + ", minTicket=" + minTicket + ", maxTicket=" + maxTicket + ", allowLoneSeat=" + allowLoneSeat + ", andOr=" + andOr);
        try {
            if (age.isPresent() && age.get() < 0)
                throw new IllegalArgumentException("Age must be a non negative number");
            if (minTicket.isPresent() && minTicket.get() < 0)
                throw new IllegalArgumentException("Minimum ticket amount must be a non negative integer");
            if (maxTicket.isPresent() && maxTicket.get() < 0)
                throw new IllegalArgumentException("maximum ticket amount must be a non negative integer");
            eventManagementDomainService.addPurchasePolicy(username, companyId, eventId, age, minTicket, maxTicket, allowLoneSeat, andOr);
        } catch (IllegalArgumentException | DomainException e) {
            logger.info("[Event Log] Business rejection in addPolicyRule: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in addPolicyRule: " + e.getMessage(), e);
            throw e;
        }
    }
    
    public void deletePolicyRule(String username, UUID companyId, UUID eventId, UUID ruleId)
    {
        logger.info("[Event Log] Method: deletePolicyRule called with parameters: username=" + username
                + ", companyId=" + companyId + "ruleId=" + ruleId);
        try {
            eventManagementDomainService.deletePurchasePolicy(username, companyId, eventId, ruleId);
        } catch (IllegalArgumentException | DomainException e) {
            logger.info("[Event Log] Business rejection in deletePolicyRule: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in deletePolicyRule: " + e.getMessage(), e);
            throw e;
        }
    }


    public void addConditionalDiscount(String username, UUID comapnyId, UUID eventId ,LocalDate fromDate, LocalDate toDate, float discountPrecent, int requiredTickets, int appliedTickets)
    {
        logger.info("[Event Log] Method: addConditionalDiscount called with parameters: username=" + username
                + ", comapnyId=" + comapnyId + ", eventId=" + eventId + ", fromDate=" + fromDate
                + ", toDate=" + toDate + ", discountPrecent=" + discountPrecent
                + ", requiredTickets=" + requiredTickets + ", appliedTickets=" + appliedTickets);
        try {
            if(toDate == null)
                throw new IllegalArgumentException("toDate is required");
            if(toDate.isBefore(LocalDate.now()))
                throw new IllegalArgumentException("toDate is before today");
            if(discountPrecent > 100.0f || discountPrecent < 0.0f)
                throw new IllegalArgumentException("Discount precent must be between 0 and 100");
            if(requiredTickets < 0 )
                throw new IllegalArgumentException("Required tickets must be non negative integers");
            if(appliedTickets < 0 )
                throw new IllegalArgumentException("Applied tickets must be non negative integers");
            eventManagementDomainService.addConditionalDiscount(username, comapnyId, eventId, fromDate, toDate, discountPrecent, requiredTickets, appliedTickets);
        } catch (IllegalArgumentException | DomainException e) {
            logger.info("[Event Log] Business rejection in addConditionalDiscount: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in addConditionalDiscount: " + e.getMessage(), e);
            throw e;
        }
    }

    public void addCouponCode(String username, UUID companyId,UUID eventId, LocalDate fromDate, LocalDate toDate, float discountPrecent, String code)
    {
        logger.info("[Event Log] Method: addCouponCode called with parameters: username=" + username
                + ", companyId=" + companyId + ", eventId=" + eventId + ", fromDate=" + fromDate
                + ", toDate=" + toDate + ", discountPrecent=" + discountPrecent + ", code=" + code);
        try {
            if(toDate == null)
                throw new IllegalArgumentException("toDate is required");
            if(toDate.isBefore(LocalDate.now()))
                throw new IllegalArgumentException("toDate is before today");
            if(discountPrecent > 100.0f || discountPrecent < 0.0f)
                throw new IllegalArgumentException("Discount precent must be between 0 and 100");
            eventManagementDomainService.addCouponCode(username, companyId, eventId, fromDate, toDate, discountPrecent, code);
        } catch (IllegalArgumentException | DomainException e) {
            logger.info("[Event Log] Business rejection in addCouponCode: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in addCouponCode: " + e.getMessage(), e);
            throw e;
        }
    }
    
    public void removeDiscount(String username, UUID compnayId, UUID eventId, UUID discountId)
    {
        logger.info("[Event Log] Method: removeDiscount called with parameters: username=" + username
                + ", compnayId=" + compnayId + ", eventId=" + eventId + ", discountId=" + discountId);
        try {
            eventManagementDomainService.removeDiscount(username, compnayId, eventId, discountId);
        } catch (IllegalArgumentException | DomainException e) {
            logger.info("[Event Log] Business rejection in removeDiscount: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in removeDiscount: " + e.getMessage(), e);
            throw e;
        }
    }

    public void rateEvent(UUID userID, UUID eventID, int rating)
    {
        logger.info("[Event Log] Method: rateEvent called with parameters: userID=" + userID
                + ", eventID=" + eventID + ", rating=" + rating);
        try {
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
        } catch (IllegalArgumentException | DomainException e) {
            logger.info("[Event Log] Business rejection in rateEvent: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in rateEvent: " + e.getMessage(), e);
            throw new DomainException(e.getMessage());
        }
    }

    // ================================================================
    //  Purchase history
    // ================================================================

    public List<PurchaseHistoryDTO> getEventPurchaseHistoryForOwner(String ownerUsername, UUID eventId) {
        if (ownerUsername == null || ownerUsername.isBlank())
            throw new IllegalArgumentException("Owner username is required");
        if (eventId == null)
            throw new IllegalArgumentException("Event ID is required");

        List<PurchaseHistory> domainHistories =
                eventManagementDomainService.getEventPurchaseHistory(ownerUsername, eventId);

        return domainHistories.stream()
                .map(this::toPurchaseHistoryDTO)
                .toList();
    }

    // ================================================================
    //  Purchase policy
    // ================================================================

    public void addPolicyRule(String username, UUID companyId, UUID eventId,
                              Float age, Integer minTicket,
                              Integer maxTicket, Boolean allowLoneSeat) {
        if (age != null && age < 0)
            throw new IllegalArgumentException("Age must be a non negative number");
        if (minTicket != null && minTicket < 0)
            throw new IllegalArgumentException("Minimum ticket amount must be a non negative integer");
        if (maxTicket != null && maxTicket < 0)
            throw new IllegalArgumentException("Maximum ticket amount must be a non negative integer");

        eventManagementDomainService.addPurchasePolicy(
                username, companyId, eventId,
                Optional.ofNullable(age),
                Optional.ofNullable(minTicket),
                Optional.ofNullable(maxTicket),
                Optional.ofNullable(allowLoneSeat));
    }

    public void deletePolicyRule(String username, UUID companyId, UUID eventId,
                                 boolean age, boolean minTicket,
                                 boolean maxTicket, boolean allowLoneSeat) {
        eventManagementDomainService.deletePurchasePolicy(
                username, companyId, eventId, age, minTicket, maxTicket, allowLoneSeat);
    }

    // ================================================================
    //  Discounts
    // ================================================================

    public void addOvertDiscount(String username, UUID companyId, UUID eventId,
                                 LocalDate fromDate, LocalDate toDate, float discountPercent) {
        if (toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if (discountPercent > 100.0f || discountPercent < 0.0f)
            throw new IllegalArgumentException("Discount percent must be between 0 and 100");
        eventManagementDomainService.addOvertDiscount(
                username, companyId, eventId, fromDate, toDate, discountPercent);
    }

    public void addConditionalDiscount(String username, UUID companyId, UUID eventId,
                                       LocalDate fromDate, LocalDate toDate, float discountPercent,
                                       int requiredTickets, int appliedTickets) {
        if (toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if (discountPercent > 100.0f || discountPercent < 0.0f)
            throw new IllegalArgumentException("Discount percent must be between 0 and 100");
        if (requiredTickets < 0)
            throw new IllegalArgumentException("Required tickets must be non negative integers");
        if (appliedTickets < 0)
            throw new IllegalArgumentException("Applied tickets must be non negative integers");
        eventManagementDomainService.addConditionalDiscount(
                username, companyId, eventId, fromDate, toDate,
                discountPercent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(String username, UUID companyId, UUID eventId,
                              LocalDate fromDate, LocalDate toDate, float discountPercent, String code) {
        if (toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if (discountPercent > 100.0f || discountPercent < 0.0f)
            throw new IllegalArgumentException("Discount percent must be between 0 and 100");
        eventManagementDomainService.addCouponCode(
                username, companyId, eventId, fromDate, toDate, discountPercent, code);
    }

    public void removeDiscount(String username, UUID companyId, UUID eventId, UUID discountId) {
        eventManagementDomainService.removeDiscount(username, companyId, eventId, discountId);
    }

    // ================================================================
    //  Rating
    // ================================================================

    public void rateEvent(UUID userId, UUID eventId, int rating) {
        if (rating < 0 || rating > 5)
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        try {
            eventManagementDomainService.rateEvent(userId, eventId, rating);
        } catch (DomainException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    // ================================================================
    //  Browsing & search
    // ================================================================


    public List<CompanyCatalogDto> browseCatalog() {
        logger.info("[Event Log] Method: browseCatalog called with parameters: (none)");
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
            return out;
        } catch (IllegalArgumentException | DomainException e) {
            logger.info("[Event Log] Business rejection in browseCatalog: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in browseCatalog: " + e.getMessage(), e);
            throw e;
        }
    }

    public EventDetailsDto getEventDetails(UUID eventId) {
        logger.info("[Event Log] Method: getEventDetails called with parameters: eventId=" + eventId);
        try {
            if (eventId == null) {
                throw new IllegalArgumentException("eventId is required");
            }
            Event e = eventManagementDomainService.getEventForView(eventId);
            return toDetails(e);
        } catch (IllegalArgumentException | DomainException ex) {
            logger.info("[Event Log] Business rejection in getEventDetails: " + ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "[Error Log] System error in getEventDetails: " + ex.getMessage(), ex);
            throw ex;
        }
    }

    public List<EventSummaryDto> searchEvents(EventSearchCriteria criteria) {
        logger.info("[Event Log] Method: searchEvents called with parameters: criteria=" + criteria);
        try {
            EventSearchCriteria c = (criteria == null) ? EventSearchCriteria.empty() : criteria;
            validateCriteria(c);
            List<Event> matches = eventManagementDomainService.searchEvents(c);
            List<EventSummaryDto> out = new ArrayList<>();
            for (Event e : matches) {
                out.add(toSummary(e));
            }
            return out;
        } catch (IllegalArgumentException | DomainException ex) {
            logger.info("[Event Log] Business rejection in searchEvents: " + ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "[Error Log] System error in searchEvents: " + ex.getMessage(), ex);
            throw ex;
        }
    }

    public List<EventSummaryDto> searchEventsByCompany(UUID companyId, EventSearchCriteria criteria) {
        logger.info("[Event Log] Method: searchEventsByCompany called with parameters: companyId="
                + companyId + ", criteria=" + criteria);
        try {
            if (companyId == null) {
                throw new IllegalArgumentException("companyId is required");
            }
            EventSearchCriteria c = (criteria == null) ? EventSearchCriteria.empty() : criteria;
            EventSearchCriteria scoped = c.withCompanyId(companyId);
            validateCriteria(scoped);
            List<Event> matches = eventManagementDomainService.searchEvents(scoped);
            List<EventSummaryDto> out = new ArrayList<>();
            for (Event e : matches) {
                out.add(toSummary(e));
            }
            return out;
        } catch (IllegalArgumentException | DomainException ex) {
            logger.info("[Event Log] Business rejection in searchEventsByCompany: " + ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            logger.log(Level.SEVERE, "[Error Log] System error in searchEventsByCompany: " + ex.getMessage(), ex);
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

    // ================================================================
    //  PRIVATE MAPPERS — Domain → DTO
    // ================================================================

    private EventSummaryDto toSummary(Event e) {
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

    private EventDetailsDto toDetails(Event e) {
        List<AreaSummaryDto> areas = new ArrayList<>();
        for (Area a : e.getLayout().getAreasView()) {
            String kind = (a instanceof StandingArea) ? "STANDING"
                    : (a instanceof SittingArea) ? "SITTING" : "UNKNOWN";
            areas.add(new AreaSummaryDto(a.getAreaId(), kind, a.getPrice()));
        }
        List<String> purchaseRules = new ArrayList<>();
        purchaseRules.add(e.getPurchasePolicy().getClass().getSimpleName());
        List<String> discountRules = new ArrayList<>();
        for (IDiscountRule r : e.getDiscountPolicy().getDiscountRules()) {
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

    private PurchaseHistoryDTO toPurchaseHistoryDTO(PurchaseHistory domainHistory) {
        if (domainHistory == null) {
            return null;
        }
        PurchaseHistoryDTO dto = new PurchaseHistoryDTO();
        dto.userId = domainHistory.getUserId();
        dto.eventId = domainHistory.getEventId();
        dto.ticketIds = domainHistory.getTicketIds();
        dto.purchaseDate = domainHistory.getPurchaseDate();

        if (domainHistory.getPayment() != null) {
            dto.totalPaid = domainHistory.getPayment().getTotal();
            dto.paymentInfo = domainHistory.getPayment().getPaymentInfo();
        } else {
            dto.totalPaid = 0.0;
            dto.paymentInfo = "N/A";
        }
        return dto;
    }

    public static EventSearchCriteria toDomainCriteria(
            String text, String location,
            Double priceMin, Double priceMax,
            LocalDateTime dateFrom, LocalDateTime dateTo,
            Double minEventRating, Double minCompanyRating,
            UUID companyId) {
        return new EventSearchCriteria(
                Optional.ofNullable(text),
                Optional.ofNullable(location),
                Optional.ofNullable(priceMin),
                Optional.ofNullable(priceMax),
                Optional.ofNullable(dateFrom),
                Optional.ofNullable(dateTo),
                Optional.ofNullable(minEventRating),
                Optional.ofNullable(minCompanyRating),
                Optional.ofNullable(companyId));
    }
}