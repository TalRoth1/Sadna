package org.example.DomainLayer;

import org.example.DomainLayer.EventAggregate.Event;

public class EventManagementDomainService
{
    private final IEventRepository eventRepository;

    //האם צריך לוודא שה-user שמבקש לעשות rating קיים ב-repo או שזאת הנחה?

    public EventManagementDomainService(IEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public void rateEvent(String userID, int eventID, int rating)
    {
        Event event = eventRepository.getById(eventID);

        if (event == null)
            throw new DomainException("Event not found while rating");

        if (userID == null || userID.isBlank())
            throw new DomainException("User not found while rating");

        event.addRating(userID, rating);
        eventRepository.save(event);
    }
}
