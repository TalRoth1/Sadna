package org.example.DomainLayer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventSearchCriteria;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.ICompanyMember;
import org.example.DomainLayer.UserAggregate.User;

public class EventManagementDomainService {

    private final IEventRepository eventRepository;
    private final IHistoryRepository historyRepository;
    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;

    public EventManagementDomainService(IEventRepository eventRepository,
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

    public void addPurchasePolicy(String username, UUID companyId, UUID eventId, Optional<Float> age,
            Optional<Integer> minTicket, Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat, boolean andOr) {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("Event not found");
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        if (!userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_POLICIES, eventId))
            throw new IllegalArgumentException("User has no permissions to change event policies");
        event.addPurchasePolicy(age, minTicket, maxTicket, allowLoneSeat, andOr);
    }

    public void deletePurchasePolicy(String username, UUID companyId, UUID eventId, UUID ruleId) {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("Event not found");
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        if (!userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_POLICIES, eventId))
            throw new IllegalArgumentException("User has no permissions to change event policies");
        event.deletePurchaseRule(ruleId);
    }

    public void addOvertDiscount(String username, UUID companyId, UUID eventId, LocalDate fromDate, LocalDate toDate,
            float discountPrecent) {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("Event not found");
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        if (!userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_POLICIES, eventId))
            throw new IllegalArgumentException("User has no permissions to change event policies");
        event.addOvertDiscount(fromDate, toDate, discountPrecent);
    }

    public void addConditionalDiscount(String username, UUID companyId, UUID eventId, LocalDate fromDate,
            LocalDate toDate, float discountPrecent, int requiredTickets, int appliedTickets) {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("Event not found");
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        if (!userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_POLICIES, eventId))
            throw new IllegalArgumentException("User has no permissions to change event policies");
        event.addConditionalDiscount(fromDate, toDate, discountPrecent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(String username, UUID companyId, UUID eventId, LocalDate fromDate, LocalDate toDate,
            float discountPrecent, String code) {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("Event not found");
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        if (!userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_POLICIES, eventId))
            throw new IllegalArgumentException("User has no permissions to change event policies");
        event.addCouponCode(fromDate, toDate, discountPrecent, code);
    }

    public void removeDiscount(String username, UUID companyId, UUID eventId, UUID discountId) {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("Event not found");
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        User user = userRepository.findByEmail(username).orElse(null);
        if (user == null)
            throw new IllegalArgumentException("User not found");
        if (!user.hasPremisions(companyId, CompanyPermission.MANAGE_POLICIES, eventId))
            throw new IllegalArgumentException("User has no permissions to change event policies");
        event.removeDiscount(discountId);
    }

    public void rateEvent(UUID userID, UUID eventID, int rating) {
        Event event = eventRepository.getById(eventID);

        if (event == null)
            throw new DomainException("Event not found while rating");

        if (userID == null)
            throw new DomainException("User not found while rating");

        event.addRating(userID, rating);
        eventRepository.save(event);
    }

    public void addEvent(UUID eventId, UUID companyId, String eventManagerEmail, String name,
            LocalDateTime date, String location, String artist, String type, EventStatus status, String description) {
        if (eventRepository.getById(eventId) != null) {
            throw new DomainException("Event already exists: " + eventId);
        }

        if (companyId == null) {
            throw new DomainException("Company not found");
        }

        if (eventManagerEmail == null || eventManagerEmail.isBlank()) {
            throw new IllegalArgumentException("Event manager email is required");
        }

        User eventManager = userRepository.findByEmail(eventManagerEmail)
                .orElseThrow(() -> new DomainException("Event manager not found"));

        ICompanyMember managerRole = eventManager.getCompanyRole(companyId);
        if (managerRole == null) {
            throw new DomainException("Event manager is not a member of the company");
        }

        Event event = new Event(eventId, companyId, date, location, artist, type, status);
        if (name != null) {
            event.setName(name);
        }
        if (description != null) {
            event.setDescription(description);
        }
        eventRepository.save(event);

        managerRole.getEventsIds().add(eventId);
    }

    public Set<UUID> editEvent(UUID eventId,
                            String name,
                            LocalDateTime date,
                            String location,
                            String artist,
                            String type,
                            EventStatus status,
                            String description) {

        Event event = eventRepository.getById(eventId);

        if (event == null) {
            throw new DomainException("Event not found");
        }

        if (name != null) {
            event.setName(name);
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

        if (description != null) {
            event.setDescription(description);
        }

    Set<UUID> participants = new HashSet<>();

    for (PurchaseHistory purchase : historyRepository.getAll()) {
        if (purchase.getEventId().equals(eventId)) {
            participants.add(purchase.getUserId());
        }
    }

    eventRepository.save(event);

    return participants;
}

    public boolean deleteEvent(UUID eventId, String userEmail, String eventManagerEmail) {
        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new DomainException("Event not found");
        }

        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("User email is required");
        }
        if (eventManagerEmail == null || eventManagerEmail.isBlank()) {
            throw new IllegalArgumentException("Event manager email is required");
        }

        UUID companyId = event.getCompanyId();
        boolean isManager = userEmail.trim().equalsIgnoreCase(eventManagerEmail.trim());
        boolean hasInventoryPermission = userRepository.hasPermission(
                userEmail,
                companyId,
                CompanyPermission.MANAGE_INVENTORY,
                eventId);

        if (!isManager && !hasInventoryPermission) {
            throw new DomainException("User is not authorized to delete this event");
        }

        User eventManager = userRepository.findByEmail(eventManagerEmail)
                .orElseThrow(() -> new DomainException("Event manager not found"));

        ICompanyMember managerRole = eventManager.getCompanyRole(companyId);
        if (managerRole == null) {
            throw new DomainException("Event manager is not a member of the company");
        }
        if (!managerRole.getEventsIds().contains(eventId)) {
            throw new DomainException("Event manager is not in charge of this event");
        }

        managerRole.getEventsIds().remove(eventId);
        eventRepository.delete(eventId);
        return true;
    }

    public void addStandingTickets(UUID eventId, UUID areaId, int count) {
        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new DomainException("Event not found");
        }
        event.addStandingTickets(areaId, count);
        eventRepository.save(event);
    }

    public void addSittingTickets(UUID eventId, UUID areaId, int rows, int seatsPerRow) {
        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new DomainException("Event not found");
        }
        event.addSittingTickets(areaId, rows, seatsPerRow);
        eventRepository.save(event);
    }

    // list all currently active production companies.
    public List<Company> getActiveCompanies() {
        return companyRepository.getAllActive();
    }

    /** publicly-visible events of a given company (used to build the catalog). */
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

    // fetch a single event for read-only display.
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

    /**
     * Null-safe lookup used by DTO mappers to denormalize event fields
     * (name/date/location) without throwing when the id is unknown or null.
     */
    public Event findEventById(UUID eventId) {
        if (eventId == null) {
            return null;
        }
        return eventRepository.getById(eventId);
    }

    /**
     * Null-safe lookup used by DTO mappers to denormalize company fields
     * (name/rating) onto event summaries. Returns null when the id is
     * unknown, null, or when the repository hands us a null Optional
     * (defensive: some mocks return null instead of Optional.empty()).
     */
    public Company findCompanyById(UUID companyId) {
        if (companyId == null) {
            return null;
        }
        Optional<Company> result = companyRepository.findByID(companyId);
        return (result == null) ? null : result.orElse(null);
    }

    /** UC filter events by criteria (companyId is just one optional filter). */
    public List<Event> searchEvents(EventSearchCriteria criteria) {
        EventSearchCriteria c = (criteria == null) ? EventSearchCriteria.empty() : criteria;
        List<Event> out = new ArrayList<>();
        for (Event e : eventRepository.getAll()) {
            Optional<Company> co = companyRepository.findByID(e.getCompanyId());
            if (co.isEmpty() || !co.get().isActive()) {
                continue;
            }
            if (e.matches(c, co.get().getRating())) {
                out.add(e);
            }
        }
        return out;
    }

    public List<Event> getEventsForUserInCompany(String userEmail, UUID companyId) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        List<Event> out = new ArrayList<>();
        User user = userRepository.findByEmail(userEmail).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (!user.isCompanyMember(companyId)) {
            throw new IllegalArgumentException("User is not a member of the company");
        }
        ICompanyMember userRole = user.getCompanyRole(companyId);
        userRole.getEventsIds().forEach(eid -> {
            Event event = eventRepository.getById(eid);
            if (event != null) {
                out.add(event);
            }
        });
        return out;
    }

}