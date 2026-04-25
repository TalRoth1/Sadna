package org.example.DomainLayer;

import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

import java.util.List;

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

    public List<PurchaseHistory> getEventPurchaseHistory(String username, int eventId) {
        Event event = eventRepository.getById(eventId);

        if (event == null) {
            throw new DomainException("Event not found");
        }

        if (!companyRepository.isOwner(username, event.getCompanyId())) {
            throw new DomainException("User is not authorized to view this event purchase history");
        }

        return historyRepository.getByEventId(eventId);
    }

}