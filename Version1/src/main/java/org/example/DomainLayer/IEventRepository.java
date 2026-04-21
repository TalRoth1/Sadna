package org.example.DomainLayer;

import org.example.DomainLayer.EventAggregate.Event;

public interface IEventRepository {
    Event findByID(int eventID);

    void save(Event event);

    void delete(int eventId);

    /** Returns the next unique event id for new aggregates (simple persistence implementations). */
    int allocateNextEventId();
}
