package org.example.InfrastructureLayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.EventAggregate.Event;

/**
 * In-memory implementation of IEventRepository for development and tests.
 */
public class InMemoryEventRepository implements IEventRepository {
    private final Map<UUID, Event> eventsById = new HashMap<>();

    @Override
    public synchronized Event getById(UUID eventID) {
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
    public synchronized void delete(UUID eventId) {
        eventsById.remove(eventId);
    }

    /** Test helper: register an event under its {@link Event#getEventId()}. */
    public void put(Event event) {
        save(event);
    }

    @Override
    public List<Event> getAll() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAll'");
    }
}
