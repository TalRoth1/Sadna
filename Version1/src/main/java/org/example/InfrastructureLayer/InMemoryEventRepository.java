package org.example.InfrastructureLayer;

import java.util.HashMap;
import java.util.Map;

import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.EventAggregate.Event;

/**
 * In-memory implementation of IEventRepository for development and tests.
 */
public class InMemoryEventRepository implements IEventRepository {
    private final Map<Integer, Event> eventsById = new HashMap<>();
    private int nextEventId = 1;

    @Override
    public synchronized Event findByID(int eventID) {
        return eventsById.get(eventID);
    }

    @Override
    public synchronized void save(Event event) {
        if (event == null) {
            throw new IllegalArgumentException("event cannot be null");
        }
        eventsById.put(event.getEventId(), event);
    }

    @Override
    public synchronized void delete(int eventId) {
        eventsById.remove(eventId);
    }

    @Override
    public synchronized int allocateNextEventId() {
        return nextEventId++;
    }

    /** Test helper: register an event under its {@link Event#getEventId()}. */
    public void put(Event event) {
        save(event);
        if (event.getEventId() >= nextEventId) {
            nextEventId = event.getEventId() + 1;
        }
    }
}
