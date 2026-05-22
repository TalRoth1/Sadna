package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.AreaSummaryDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.CompanyCatalogDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.DiscountPolicyDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.DiscountRuleDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.EventDetailsDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.EventSummaryDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.PurchasePolicyDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.PurchaseRuleDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.TicketDetailsDto;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.Area;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventSearchCriteria;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.SittingTicket;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.EventAggregate.Ticket;
import org.example.DomainLayer.EventAggregate.TicketStatus;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PolicyManagment.AgeRule;
import org.example.DomainLayer.PolicyManagment.ConditionalDiscount;
import org.example.DomainLayer.PolicyManagment.CouponCode;
import org.example.DomainLayer.PolicyManagment.IDiscountRule;
import org.example.DomainLayer.PolicyManagment.IPurchaseRule;
import org.example.DomainLayer.PolicyManagment.LoneSeatRule;
import org.example.DomainLayer.PolicyManagment.MaxTicketRule;
import org.example.DomainLayer.PolicyManagment.MinTicketRule;
import org.example.DomainLayer.PolicyManagment.OvertDiscount;
import org.example.DomainLayer.PolicyManagment.PurchaseComposite;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;

import org.springframework.stereotype.Service;

@Service
public class EventService {
    private static final Logger logger = Logger.getLogger(EventService.class.getName());
    private final EventManagementDomainService eventManagementDomainService;
    private final INotifier notifier;

    public EventService(EventManagementDomainService eventManagementDomainService, INotifier notifier) {
        this.eventManagementDomainService = eventManagementDomainService;
        this.notifier = notifier;
    }

    public EventDetailsDto addEvent(UUID eventId, UUID companyId, String name, LocalDateTime date, String location,
                                    String artist, String type, EventStatus status) {
        logger.info("[Event Log] Method: addEvent called");

        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (companyId == null) {
            throw new IllegalArgumentException("companyId is required");
        }

        eventManagementDomainService.addEvent(eventId, companyId, name, date, location, artist, type, status);

        Event event = eventManagementDomainService.getEventForView(eventId);
        return toDetails(event);
    }

    public EventSummaryDto editEvent(UUID eventId,
                                 String name,
                                 LocalDateTime date,
                                 String location,
                                 String artist,
                                 String type,
                                 EventStatus status) {

    logger.info("[Event Log] Method: editEvent called with parameters: eventId=" + eventId
            + ", date=" + date + ", location=" + location + ", artist=" + artist
            + ", type=" + type + ", status=" + status);

    try {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }

        Set<UUID> participants = eventManagementDomainService.editEvent(
                eventId,
                name,
                date,
                location,
                artist,
                type,
                status
        );

        for (UUID uid : participants) {
            notifier.notifyUser(uid,
                    "Event: " + eventId + " has been changed");
        }

        Event event = eventManagementDomainService.getEventForView(eventId);

        return toSummary(event);

    } catch (IllegalArgumentException | DomainException e) {
        logger.info("[Event Log] Business rejection in editEvent: " + e.getMessage());
        throw e;

    } catch (RuntimeException e) {
        logger.log(Level.SEVERE,
                "[Error Log] System error in editEvent: " + e.getMessage(),
                e);
        throw e;
    }
}

    public void addPolicyRule(String username, UUID companyId, UUID eventId,
                              Float age, Integer minTicket, Integer maxTicket, Boolean allowLoneSeat) {
        addPolicyRule(
                username,
                companyId,
                eventId,
                Optional.ofNullable(age),
                Optional.ofNullable(minTicket),
                Optional.ofNullable(maxTicket),
                Optional.ofNullable(allowLoneSeat),
                true
        );
    }

    public boolean deleteEvent(UUID eventId) {
        logger.info("[Event Log] Method: deleteEvent called with parameters: eventId=" + eventId);
        try {
            if (eventId == null) {
                throw new IllegalArgumentException("eventId is required");
            }
            return eventManagementDomainService.deleteEvent(eventId);
        } catch (IllegalArgumentException | DomainException e) {
            logger.info("[Event Log] Business rejection in deleteEvent: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in deleteEvent: " + e.getMessage(), e);
            throw e;
        }
    }

    public void addStandingTickets(UUID eventId, UUID areaId, int count) {
        logger.info("[Event Log] Method: addStandingTickets called with parameters: eventId=" + eventId
                + ", areaId=" + areaId + ", count=" + count);
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

    public List<PurchaseHistoryDTO> getEventPurchaseHistoryForOwner(String ownerUsername, UUID eventId) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("eventId is required");
        }

        List<PurchaseHistory> histories =
                eventManagementDomainService.getEventPurchaseHistory(ownerUsername, eventId);

        List<PurchaseHistoryDTO> out = new ArrayList<>();
        for (PurchaseHistory history : histories) {
            out.add(toPurchaseHistoryDTO(history));
        }
        return out;
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

    public void addOvertDiscount(String username, UUID companyId, UUID eventId ,LocalDate fromDate, LocalDate toDate, float discountPrecent)
    {
        logger.info("[Event Log] Method: addOvertDiscount called with parameters: username=" + username
                + ", companyId=" + companyId + ", eventId=" + eventId + ", fromDate=" + fromDate
                + ", toDate=" + toDate + ", discountPrecent=" + discountPrecent);
        try {
            if(toDate == null)
                throw new IllegalArgumentException("toDate is required");
            if(toDate.isBefore(LocalDate.now()))
                throw new IllegalArgumentException("toDate is before today");
            if(discountPrecent > 100.0f || discountPrecent < 0.0f)
                throw new IllegalArgumentException("Discount precent must be between 0 and 100");
            eventManagementDomainService.addOvertDiscount(username, companyId, eventId, fromDate, toDate, discountPrecent);
        } catch (IllegalArgumentException | DomainException e) {
            logger.info("[Event Log] Business rejection in addOvertDiscount: " + e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "[Error Log] System error in addOvertDiscount: " + e.getMessage(), e);
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

    /**
     * Carrier for the aggregated inventory snapshot of an event — used by both
     * {@link #toSummary(Event)} (UC 2.3.1) and {@link #toDetails(Event)}
     * (UC 2.1 extended view) so the calculation lives in exactly one place.
     */
    private record InventorySnapshot(double priceMin, double priceMax,
                                     int availableTickets, int totalTickets) {
    }

    private static InventorySnapshot snapshotOf(Event e) {
        double priceMin = 0.0;
        double priceMax = 0.0;
        List<Area> areas = e.getLayout().getAreasView();
        if (!areas.isEmpty()) {
            priceMin = areas.get(0).getPrice();
            priceMax = areas.get(0).getPrice();
            for (Area a : areas) {
                double p = a.getPrice();
                if (p < priceMin) priceMin = p;
                if (p > priceMax) priceMax = p;
            }
        }

        int totalTickets = e.getTicketsView().size();
        int availableTickets = 0;
        for (Ticket t : e.getTicketsView().values()) {
            if (t.getStatus() == TicketStatus.AVAILABLE) {
                availableTickets++;
            }
        }
        return new InventorySnapshot(priceMin, priceMax, availableTickets, totalTickets);
    }

    /**
     * Map a domain Event to the flat summary row consumed by the Event Search
     * page (UC 2.3.1). The card needs company info, a price range across the
     * event's areas and live ticket counts — all derived here so the client
     * doesn't need follow-up calls.
     */
    private EventSummaryDto toSummary(Event e) {
        Company company = eventManagementDomainService.findCompanyById(e.getCompanyId());
        String companyName = (company == null) ? "" : company.getName();
        double companyRating = (company == null) ? 0.0 : company.getRating();

        InventorySnapshot snap = snapshotOf(e);

        return new EventSummaryDto(
                e.getEventId(),
                e.getCompanyId(),
                companyName,
                companyRating,
                e.getName(),
                e.getArtist(),
                e.getType(),
                e.getDate(),
                e.getLocation(),
                e.getRating(),
                snap.priceMin(),
                snap.priceMax(),
                snap.availableTickets(),
                snap.totalTickets());
    }

    /**
     * Map a domain Event to the extended details payload consumed by the
     * Event Details page. Compared to {@link #toSummary(Event)} this also
     * carries the venue layout, the per-ticket inventory snapshot, the
     * structured purchase + discount policies, and the lottery id.
     */
    private EventDetailsDto toDetails(Event e) {
        Company company = eventManagementDomainService.findCompanyById(e.getCompanyId());
        String companyName = (company == null) ? "" : company.getName();
        double companyRating = (company == null) ? 0.0 : company.getRating();

        List<AreaSummaryDto> areas = new ArrayList<>();
        for (Area a : e.getLayout().getAreasView()) {
            String kind = (a instanceof StandingArea) ? "STANDING"
                    : (a instanceof SittingArea) ? "SITTING" : "UNKNOWN";
            areas.add(new AreaSummaryDto(
                    a.getAreaId(), kind, a.getPrice(),
                    new ArrayList<>(a.getTicketIdsView())));
        }

        List<TicketDetailsDto> tickets = new ArrayList<>();
        for (Ticket t : e.getTicketsView().values()) {
            Integer row = null;
            Integer seat = null;
            if (t instanceof SittingTicket st) {
                row = st.getSeatRow();
                seat = st.getSeatNumber();
            }
            tickets.add(new TicketDetailsDto(
                    t.getTicketId(), t.getAreaId(), t.getStatus(),
                    t.getPrice(), row, seat));
        }

        InventorySnapshot snap = snapshotOf(e);

        return new EventDetailsDto(
                e.getEventId(),
                e.getCompanyId(),
                companyName,
                companyRating,
                e.getName(),
                e.getArtist(),
                e.getType(),
                e.getDate(),
                e.getLocation(),
                e.getTagsView(),
                e.getStatus(),
                e.getRating(),
                e.getLotteryId(),
                snap.priceMin(),
                snap.priceMax(),
                snap.availableTickets(),
                snap.totalTickets(),
                areas,
                tickets,
                toPurchasePolicyDto(e),
                toDiscountPolicyDto(e));
    }

    /**
     * Walk the (possibly composite) purchase-rule tree on the event and
     * collect all leaf rules into a flat list. The frontend just needs the
     * scalar parameters (minAge, minTickets, maxTickets, allowLoneSeat) to
     * render the "purchase rules" bullets, so composition nodes are skipped.
     */
    private static PurchasePolicyDto toPurchasePolicyDto(Event e) {
        List<PurchaseRuleDto> out = new ArrayList<>();
        collectPurchaseLeaves(e.getPurchasePolicy().getRulesView(), out);
        return new PurchasePolicyDto(out);
    }

    private static void collectPurchaseLeaves(IPurchaseRule rule, List<PurchaseRuleDto> out) {
        if (rule == null) {
            return;
        }
        if (rule instanceof PurchaseComposite composite) {
            collectPurchaseLeaves(composite.getLeftRule(), out);
            collectPurchaseLeaves(composite.getRightRule(), out);
            return;
        }
        if (rule instanceof AgeRule age) {
            out.add(new PurchaseRuleDto(age.getId(), "AGE",
                    age.getMinAge(), null, null, null));
        } else if (rule instanceof MinTicketRule min) {
            out.add(new PurchaseRuleDto(min.getId(), "MIN_TICKETS",
                    null, min.getMinTicket(), null, null));
        } else if (rule instanceof MaxTicketRule max) {
            out.add(new PurchaseRuleDto(max.getId(), "MAX_TICKETS",
                    null, null, max.getMaxTicket(), null));
        } else if (rule instanceof LoneSeatRule lone) {
            out.add(new PurchaseRuleDto(lone.getId(), "LONE_SEAT",
                    null, null, null, lone.isAllowLoneSeat()));
        }
    }

    private static DiscountPolicyDto toDiscountPolicyDto(Event e) {
        List<DiscountRuleDto> out = new ArrayList<>();
        for (IDiscountRule r : e.getDiscountPolicy().getDiscountRules()) {
            if (r instanceof OvertDiscount overt) {
                out.add(new DiscountRuleDto(
                        overt.getId(), "OVERT",
                        overt.getFromDate(), overt.getToDate(),
                        overt.getDiscountPercent(), null, null, null));
            } else if (r instanceof ConditionalDiscount cond) {
                out.add(new DiscountRuleDto(
                        cond.getId(), "CONDITIONAL",
                        cond.getFromDate(), cond.getToDate(),
                        cond.getDiscountPercent(),
                        cond.getRequiredTickets(), cond.getAppliedTickets(), null));
            } else if (r instanceof CouponCode coupon) {
                out.add(new DiscountRuleDto(
                        coupon.getId(), "COUPON",
                        coupon.getFromDate(), coupon.getToDate(),
                        coupon.getDiscountPercent(), null, null,
                        coupon.getCode()));
            }
        }
        return new DiscountPolicyDto(out);
    }

    private PurchaseHistoryDTO toPurchaseHistoryDTO(PurchaseHistory history) {
        PurchaseHistoryDTO dto = new PurchaseHistoryDTO();
        dto.userId = history.getUserId();
        dto.eventId = history.getEventId();
        dto.ticketIds = history.getTicketIds();
        dto.purchaseDate = history.getPurchaseDate();
        dto.ticketsAmount = (dto.ticketIds == null) ? 0 : dto.ticketIds.size();

        if (history.getPayment() != null) {
            dto.paymentInfo = history.getPayment().toString();
            dto.totalPrice = history.getPayment().getTotal();
        } else {
            dto.paymentInfo = "";
            dto.totalPrice = 0.0;
        }

        Event event = eventManagementDomainService.findEventById(history.getEventId());
        if (event != null) {
            dto.eventName = event.getName();
            dto.eventDate = event.getDate();
            dto.eventLocation = event.getLocation();
        }

        return dto;
    }
    public static EventSearchCriteria toDomainCriteria(
            String text,
            String location,
            Double priceMin,
            Double priceMax,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            Double minEventRating,
            Double minCompanyRating,
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
                Optional.ofNullable(companyId)
        );
    }

}
