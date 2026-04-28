package org.example.DomainLayer.Repositories;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.EventAggregate.Event;

public class EventRepository implements IEventRepository {

    public Map<UUID, Event> events;

    public Event getById(UUID eventId)
    {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getById'");
    }

    @Override
    public List<Event> getAll() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAll'");
    }
    
}
