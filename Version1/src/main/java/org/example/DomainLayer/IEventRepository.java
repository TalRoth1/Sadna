package org.example.DomainLayer;

import org.example.DomainLayer.EventAggregate.Event;
import java.util.List;
import java.util.UUID;

public interface IEventRepository {
    Event getById(UUID eventId);
    List<Event> getAll();
    void save(Event event);
    void delete(UUID eventId);
}
