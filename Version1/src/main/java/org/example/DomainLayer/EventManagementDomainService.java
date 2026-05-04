package org.example.DomainLayer;

import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import java.util.ArrayList;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.EventSearchCriteria;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class EventManagementDomainService {

    private final IEventRepository eventRepository;
    private final IHistoryRepository historyRepository;
    private final ICompanyRepository companyRepository;

    public EventManagementDomainService(IEventRepository eventRepository,
                                        IHistoryRepository historyRepository,
                                        ICompanyRepository companyRepository) {
        this.eventRepository = eventRepository;
        this.historyRepository = historyRepository;
        this.companyRepository = companyRepository;
    }

    public List<PurchaseHistory> getEventPurchaseHistory(String username, UUID eventId) {
        Event event = eventRepository.getById(eventId);

        if (event == null) {
            throw new DomainException("Event not found");
        }

        if (!companyRepository.isOwner(username, event.getCompanyId())) {
            throw new DomainException("User is not authorized to view this event purchase history");
        }

        return historyRepository.getByEventId(eventId);
    }
    
    public void addPurchasePolicy(UUID eventId, Optional<Float> age, Optional<Integer> minTicket, Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("Event not found");
        event.addPurchasePolicy(age, minTicket, maxTicket, allowLoneSeat);
    }

    public void deletePurchasePolicy(UUID eventId, boolean age, boolean minTicket, boolean maxTicket, boolean allowLoneSeat)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("Event not found");
        event.deletePurchaseRule(age, minTicket, maxTicket, allowLoneSeat);
    }

    public void addOvertDiscount(UUID eventId, LocalDate fromDate, LocalDate toDate, float discountPrecent)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("Event not found");
        event.addOvertDiscount(fromDate, toDate, discountPrecent);
    }

    public void addConditionalDiscount(UUID eventId, LocalDate fromDate, LocalDate toDate, float discountPrecent, int requiredTickets, int appliedTickets)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("Event not found");
        event.addConditionalDiscount(fromDate, toDate, discountPrecent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(UUID eventId, LocalDate fromDate, LocalDate toDate, float discountPrecent, String code)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("Event not found");
        event.addCouponCode(fromDate, toDate, discountPrecent, code);
    }

    public void removeDiscount(UUID eventId, UUID discountId)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("Event not found");
        event.removeDiscount(discountId); 
    }
    
    public void rateEvent(UUID userID, UUID eventID, int rating)
    {
        Event event = eventRepository.getById(eventID);

        if (event == null)
            throw new DomainException("Event not found while rating");

        if (userID == null)
            throw new DomainException("User not found while rating");

        event.addRating(userID, rating);
        eventRepository.save(event);
    }

    public void addEvent(UUID eventId, UUID companyId, LocalDateTime date, String location,
                         String artist, String type, EventStatus status) {
        if (eventRepository.getById(eventId) != null) {
            throw new DomainException("Event already exists: " + eventId);
        }
        Event event = new Event(eventId, companyId, date, location, artist, type, status);
        eventRepository.save(event);
    }

    public boolean editEvent(UUID eventId, LocalDateTime date, String location,
                             String artist, String type, EventStatus status) {
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
        return true;
    }

    public boolean deleteEvent(UUID eventId) {
        Event event = eventRepository.getById(eventId);
        if (event == null) {
            throw new DomainException("Event not found");
        }
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

        //list all currently active production companies.
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
    
        //fetch a single event for read-only display.
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

}
