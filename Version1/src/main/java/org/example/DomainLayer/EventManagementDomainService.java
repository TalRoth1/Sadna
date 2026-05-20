package org.example.DomainLayer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventSearchCriteria;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.InfrastructureLayer.EventRepository;

public class EventManagementDomainService {

    private final IEventRepository eventRepository;
    private final IHistoryRepository historyRepository;
    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;

    public EventManagementDomainService(EventRepository eventRepository,
                                        IHistoryRepository historyRepository,
                                        ICompanyRepository companyRepository,
                                        IUserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.historyRepository = historyRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    public List<PurchaseHistory> getEventPurchaseHistory(String username, UUID eventId) {
        Event event = eventRepository.getById(eventId);

        if (event == null) {
            throw new DomainException("Event not found");
        }

        if (!userRepository.isCompanyOwner(username, event.getCompanyId())) {
            throw new DomainException("User is not authorized to view this event purchase history");
        }

        return historyRepository.getByEventId(eventId);
    }

    // ================================================================
    //  Purchase policy & discounts
    // ================================================================

    public void addPurchasePolicy(String username, UUID companyId, UUID eventId,
                                  Optional<Float> age,
                                  Optional<Integer> minTicket,
                                  Optional<Integer> maxTicket,
                                  Optional<Boolean> allowLoneSeat) {
        Event event = requireEvent(eventId);
        requirePolicyPermission(companyId, username, eventId);
        event.addPurchasePolicy(age, minTicket, maxTicket, allowLoneSeat);
    }

    public void deletePurchasePolicy(String username, UUID companyId, UUID eventId,
                                     boolean age,
                                     boolean minTicket,
                                     boolean maxTicket,
                                     boolean allowLoneSeat) {
        Event event = requireEvent(eventId);
        requirePolicyPermission(companyId, username, eventId);
        event.deletePurchaseRule(age, minTicket, maxTicket, allowLoneSeat);
    }

    public void addOvertDiscount(String username, UUID companyId, UUID eventId,
                                 LocalDate fromDate, LocalDate toDate,
                                 float discountPrecent) {
        Event event = requireEvent(eventId);
        requirePolicyPermission(companyId, username, eventId);
        event.addOvertDiscount(fromDate, toDate, discountPrecent);
    }

    public void addConditionalDiscount(String username, UUID companyId, UUID eventId,
                                       LocalDate fromDate, LocalDate toDate,
                                       float discountPrecent,
                                       int requiredTickets,
                                       int appliedTickets) {
        Event event = requireEvent(eventId);
        requirePolicyPermission(companyId, username, eventId);
        event.addConditionalDiscount(fromDate, toDate, discountPrecent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(String username, UUID companyId, UUID eventId,
                              LocalDate fromDate, LocalDate toDate,
                              float discountPrecent,
                              String code) {
        Event event = requireEvent(eventId);
        requirePolicyPermission(companyId, username, eventId);
        event.addCouponCode(fromDate, toDate, discountPrecent, code);
    }

    public void removeDiscount(String username, UUID companyId, UUID eventId, UUID discountId) {
        Event event = requireEvent(eventId);
        requirePolicyPermission(companyId, username, eventId);
        event.removeDiscount(discountId);
    }

    // ================================================================
    //  Rating
    // ================================================================

    public void rateEvent(UUID userID, UUID eventID, int rating) {
        Event event = eventRepository.getById(eventID);

        if (event == null) {
            throw new DomainException("Event not found while rating");
        }

        if (userID == null) {
            throw new DomainException("User not found while rating");
        }

        event.addRating(userID, rating);
        eventRepository.save(event);
    }

    // ================================================================
    //  Event lifecycle
    // ================================================================

    public Event addEvent(UUID eventId, UUID companyId, String name,
                          LocalDateTime date,
                          String location,
                          String artist,
                          String type,
                          EventStatus status) {
        if (eventRepository.getById(eventId) != null) {
            throw new DomainException("Event already exists: " + eventId);
        }

        Event event = new Event(eventId, companyId, date, location, artist, type, status);
        event.setName(name);
        eventRepository.save(event);
        return event;
    }

    public Event editEvent(UUID eventId,
                           LocalDateTime date,
                           String location,
                           String artist,
                           String type,
                           EventStatus status) {
        Event event = eventRepository.getById(eventId);

        if (event == null) {
            throw new DomainException("Event not found");
        }

        if (date != null) {
            event.setDate(date);
        }
        if (location != null) {
            event.setLocation(location);
        }
        if (artist != null) {
            event.setArtist(artist);
        }
        if (type != null) {
            event.setType(type);
        }
        if (status != null) {
            event.setStatus(status);
        }

        eventRepository.save(event);
        return event;
    }

    public void deleteEvent(UUID eventId) {
        Event event = eventRepository.getById(eventId);

        if (event == null) {
            throw new DomainException("Event not found");
        }

        eventRepository.delete(eventId);
    }

    // ================================================================
    //  Tickets
    // ================================================================

    public void addStandingTickets(UUID eventId, UUID areaId, int count) {
        Event event = requireEvent(eventId);
        event.addStandingTickets(areaId, count);
        eventRepository.save(event);
    }

    public void addSittingTickets(UUID eventId, UUID areaId, int rows, int seatsPerRow) {
        Event event = requireEvent(eventId);
        event.addSittingTickets(areaId, rows, seatsPerRow);
        eventRepository.save(event);
    }

    // ================================================================
    //  Browsing & search
    // ================================================================

    public List<Company> getActiveCompanies() {
        return companyRepository.getAllActive();
    }

    public List<Event> getVisibleEventsForCompany(UUID companyId) {
        if (companyId == null) {
            throw new DomainException("companyId is required");
        }

        List<Event> out = new ArrayList<>();

        for (Event e : eventRepository.getAll()) {
            if (companyId.equals(e.getCompanyId()) && e.isPubliclyVisible()) {
                out.add(e);
            }
        }

        return out;
    }

    public Event getEventForView(UUID eventId) {
        if (eventId == null) {
            throw new DomainException("eventId is required");
        }

        Event event = eventRepository.getById(eventId);

        if (event == null) {
            throw new DomainException("Event not found");
        }

        return event;
    }

    public List<Event> searchEvents(EventSearchCriteria criteria) {
        EventSearchCriteria c = (criteria == null) ? EventSearchCriteria.empty() : criteria;
        List<Event> out = new ArrayList<>();

        for (Event e : eventRepository.getAll()) {
            Optional<Company> company = companyRepository.findByID(e.getCompanyId());

            if (!company.isPresent() || !company.get().isActive()) {
                continue;
            }

            if (e.matches(c, company.get().getRating())) {
                out.add(e);
            }
        }

        return out;
    }

    // ================================================================
    //  Private helpers
    // ================================================================

    private Event requireEvent(UUID eventId) {
        Event event = eventRepository.getById(eventId);

        if (event == null) {
            throw new DomainException("Event not found");
        }

        return event;
    }

    private void requirePolicyPermission(UUID companyId, String username, UUID eventId) {
        Company company = companyRepository.findByID(companyId)
                .orElseThrow(() -> new DomainException("Company not found"));

        if (!company.hasPremision(username, CompanyPermission.MANAGE_POLICIES, eventId)) {
            throw new DomainException("User has no permissions to change event policies");
        }
    }
}