package org.example.DomainLayer;

import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.util.List;
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

    public void addAgePolicy(UUID eventId, float age)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null) 
            throw new DomainException("Event not found");
        event.addAgePolicy(age);
    }

    public void deleteAgePolicy(UUID eventId)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("event not found");
        event.deleteAgePolicy();
    }

    public void addMinTicketPolicy(UUID eventId, int minTicket)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null) 
            throw new DomainException("Event not found");
        event.addMinTicketPolicy(minTicket);
    }

    public void deleteMinTicketPolicy(UUID eventId)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("event not found");
        event.deleteMinTicketPolicy();
    }

    public void addMaxTicketPolicy(UUID eventId, int maxTicket)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null) 
            throw new DomainException("Event not found");
        event.addMaxTicketPolicy(maxTicket);
    }

    public void deleteMaxTicketPolicy(UUID eventId)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("event not found");
        event.deleteMaxTicketPolicy();
    }

    public void addLoneSeatPolicy(UUID eventId, boolean allowLoneSeat)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null) 
            throw new DomainException("Event not found");
        event.addLoneSeatPolicy(allowLoneSeat);
    }

    public void deleteLoneSeatPolicy(UUID eventId)
    {
        Event event = eventRepository.getById(eventId);
        if (event == null)
            throw new IllegalArgumentException("event not found");
        event.deleteLoneSeatPolicy();
    }

}