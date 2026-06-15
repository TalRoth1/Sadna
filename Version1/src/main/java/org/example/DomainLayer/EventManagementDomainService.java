package org.example.DomainLayer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.ApplicationLayer.IPaymentGateway;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventSearchCriteria;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.PolicyManagment.DiscountType;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.ICompanyMember;
import org.example.DomainLayer.UserAggregate.User;

import jakarta.transaction.Transactional;

public class EventManagementDomainService {

    private static final Logger logger =
            Logger.getLogger(EventManagementDomainService.class.getName());

    private final IEventRepository eventRepository;
    private final IHistoryRepository historyRepository;
    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;
    private final ILotteryRepository lotteryRepository;
    // Used to auto-refund buyers when an event is cancelled (general
    // requirement I.3). May be null in unit tests that don't exercise refunds.
    private final IPaymentGateway paymentGateway;

    public EventManagementDomainService(IEventRepository eventRepository,
            IHistoryRepository historyRepository,
            ICompanyRepository companyRepository,
            IUserRepository userRepository,
            ILotteryRepository lotteryRepository) {
        this(eventRepository, historyRepository, companyRepository,
                userRepository, lotteryRepository, null);
    }

    public EventManagementDomainService(IEventRepository eventRepository,
            IHistoryRepository historyRepository,
            ICompanyRepository companyRepository,
            IUserRepository userRepository,
            ILotteryRepository lotteryRepository,
            IPaymentGateway paymentGateway) {
        this.eventRepository = eventRepository;
        this.historyRepository = historyRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.lotteryRepository = lotteryRepository;
        this.paymentGateway = paymentGateway;
    }

    private Event requireEvent(UUID eventId) {
        Event event = eventRepository.getById(eventId);

        if (event == null) {
            throw new DomainException("Event not found");
        }

        return event;
    }

    private void requireCompanyExists(UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }

        Company company = companyRepository.findByID(companyId).orElse(null);

        if (company == null) {
            throw new IllegalArgumentException("Company not found");
        }
    }

    private void requireInventoryPermission(String username, UUID companyId, UUID eventId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }

        requireCompanyExists(companyId);

        if (!userRepository.hasPermission(username, companyId, CompanyPermission.MANAGE_INVENTORY, eventId)
                && !userRepository.hasPermission(username, companyId, CompanyPermission.CONFIGURE_LAYOUT, eventId)) {
            throw new IllegalArgumentException("User has no permissions to change event inventory");
        }
    }

    public void updateStandingArea(String username,
                                    UUID companyId,
                                    UUID eventId,
                                    UUID areaId,
                                    double price,
                                    int count) {
            Event event = requireEvent(eventId);

            if (areaId == null) {
                throw new IllegalArgumentException("areaId is required");
            }

            requireInventoryPermission(username, companyId, eventId);

            event.updateStandingArea(areaId, price, count);

            eventRepository.save(event);
        }

        public void updateSittingArea(String username,
                                    UUID companyId,
                                    UUID eventId,
                                    UUID areaId,
                                    double price,
                                    int rows,
                                    int seatsPerRow) {
            Event event = requireEvent(eventId);

            if (areaId == null) {
                throw new IllegalArgumentException("areaId is required");
            }

            requireInventoryPermission(username, companyId, eventId);

            event.updateSittingArea(areaId, price, rows, seatsPerRow);

            eventRepository.save(event);
        }

        public void deleteArea(String username,
                            UUID companyId,
                            UUID eventId,
                            UUID areaId) {
            Event event = requireEvent(eventId);

            if (areaId == null) {
                throw new IllegalArgumentException("areaId is required");
            }

            requireInventoryPermission(username, companyId, eventId);

            event.deleteArea(areaId);

            eventRepository.save(event);
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

    public boolean areLotteryWinnersDrawn(UUID eventId) {
        if (eventId == null) {
            return false;
        }

        PuchaseLottery lottery = lotteryRepository.findByEventID(eventId);

        return lottery != null && lottery.areWinnersDrawn();
    }

    public void startRegularSale(UUID eventId) {
        if (eventId == null) {
            throw new DomainException("Event id is required");
        }

        Event event = eventRepository.getById(eventId);

        if (event == null) {
            throw new DomainException("Event not found");
        }

        if (event.getLotteryId() == null || event.getLotteryId().isBlank()) {
            throw new DomainException("Event is not currently a lottery event");
        }

        PuchaseLottery lottery = lotteryRepository.findByEventID(eventId);

        if (lottery == null) {
            throw new DomainException("Lottery does not exist for this event");
        }

        if (!lottery.areWinnersDrawn()) {
            throw new DomainException("Cannot start regular sale before drawing lottery winners");
        }

        event.setLotteryId(null);
        event.setType("Regular Sale");

        eventRepository.save(event);
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
            LocalDateTime date, String location, String artist, String type, EventStatus status, String description, DiscountType discountType) {
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

        Event event = new Event(eventId, companyId, date, location, artist, type, status, discountType);
        if (name != null) {
            event.setName(name);
        }
        if (description != null) {
            event.setDescription(description);
        }
        // Persist the manager username (identifier) so DB FK to users.manager_username is satisfied
        event.setManagerUsername(eventManager.getUsername());
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

        // Capture the status before any mutation so we can detect the
        // ACTIVE/ENDED -> CANCELED transition exactly once. Editing an
        // already-cancelled event (or any edit that doesn't change the status
        // to CANCELED) must NOT trigger another round of refunds.
        EventStatus previousStatus = event.getStatus();

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

    // Event cancellation via status change: auto-refund every buyer, but only
    // on the transition INTO CANCELED (general req. I.3).
    boolean cancellationTransition =
            status == EventStatus.CANCELED && previousStatus != EventStatus.CANCELED;
    if (cancellationTransition) {
        refundEventPurchases(eventId);
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
        // The persisted events table owns the manager→event link (manager_username);
        // the in-memory eventsIds list is not rehydrated under the JPA profile.
        if (!eventManager.getUsername().equalsIgnoreCase(event.getManagerUsername())) {
            throw new DomainException("Event manager is not in charge of this event");
        }

        // Event cancellation: automatically refund every buyer before the
        // event (and its purchase records) are removed (general req. I.3).
        // Skip when the event is already CANCELED — those buyers were already
        // refunded on the cancellation transition, so refunding again would
        // double-reverse the same charges.
        if (event.getStatus() != EventStatus.CANCELED) {
            refundEventPurchases(eventId);
        }

        eventRepository.delete(eventId);
        return true;
    }

    /**
     * Best-effort automatic refund of all completed purchases for a cancelled
     * event. Each charge is reversed via the payment gateway using the stored
     * external transaction id. Failures are logged and swallowed so one bad
     * refund can't block the cancellation or the remaining refunds — operator
     * follow-up handles those.
     */
    private void refundEventPurchases(UUID eventId) {
        if (paymentGateway == null) {
            return;
        }

        for (PurchaseHistory purchase : historyRepository.getByEventId(eventId)) {
            int transactionId = purchase.getPayment() == null
                    ? -1
                    : purchase.getPayment().getTransactionId();

            if (transactionId <= 0) {
                continue;
            }

            try {
                boolean refunded = paymentGateway.refund(transactionId);
                logger.info("[EventManagementDomainService] event=" + eventId
                        + " user=" + purchase.getUserId()
                        + " tx=" + transactionId + " refunded=" + refunded);
            } catch (RuntimeException refundError) {
                logger.log(Level.WARNING,
                        "[EventManagementDomainService] refund failed event=" + eventId
                                + " tx=" + transactionId, refundError);
            }
        }
    }

    public void addStandingTickets(UUID eventId, UUID areaId, int count) {
        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new DomainException("Event not found");
        }
        event.addStandingTickets(areaId, count);
        eventRepository.save(event);
    }

    public UUID addStandingArea(UUID eventId, double price, int count) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }
        if (price < 0) {
            throw new IllegalArgumentException("price must be non-negative");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }

        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new DomainException("Event not found");
        }

        UUID areaId = UUID.randomUUID();
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.StandingArea(areaId, price));
        event.addStandingTickets(areaId, count);
        eventRepository.save(event);
        return areaId;
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

    public void saveEvent(org.example.DomainLayer.EventAggregate.Event event) {
        if (event == null) {
            throw new IllegalArgumentException("event is required");
        }
        eventRepository.save(event);
    }


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
        // Derive the member's events from the persisted events table (company_id +
        // manager_username) rather than the in-memory ICompanyMember.eventsIds list,
        // which is never rehydrated under the JPA profile. Mirrors searchEvents().
        String managerUsername = user.getUsername();
        for (Event event : eventRepository.getAll()) {
            if (companyId.equals(event.getCompanyId())
                    && managerUsername != null
                    && managerUsername.equalsIgnoreCase(event.getManagerUsername())) {
                out.add(event);
            }
        }
        return out;
    }

    @Transactional
    public void createLotteryForEvent(UUID eventId,
                                    LocalDateTime registrationOpen,
                                    LocalDateTime registrationClose) {
        Event event = eventRepository.getById(eventId);

        if (event == null) {
            throw new DomainException("Event not found");
        }

        if (event.getLotteryId() != null && !event.getLotteryId().isBlank()) {
            throw new DomainException("Event already has a lottery");
        }

        UUID lotteryId = UUID.randomUUID();

        PuchaseLottery lottery = new PuchaseLottery(
                lotteryId,
                eventId,
                registrationOpen,
                registrationClose
        );

        // חייב להיות קודם
        lotteryRepository.save(lottery);

        // רק אחרי שההגרלה קיימת ב-DB
        event.setLotteryId(lotteryId.toString());

        // עכשיו אפשר לשמור את האירוע עם foreign key תקין
        eventRepository.save(event);
    }

}
