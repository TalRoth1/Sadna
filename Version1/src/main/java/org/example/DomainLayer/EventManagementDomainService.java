package org.example.DomainLayer;

import org.example.DomainLayer.EventAggregate.Event;

public class EventManagementDomainService
{
    private final IEventRepository eventRepository;

    public EventManagementDomainService(IEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public void rateEvent(int userID, int eventID, int rating)
    {
        Event event = eventRepository.getById(eventID);

        if (event == null)
            throw new DomainException("Event not found while rating");

        if (userID == null)
            throw new DomainException("User not found while rating");


    }
}
