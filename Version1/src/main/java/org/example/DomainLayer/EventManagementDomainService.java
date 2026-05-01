package org.example.DomainLayer;

import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.time.LocalDate;
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

}
