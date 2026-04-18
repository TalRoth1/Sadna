package org.example.DomainLayer;

import org.example.DomainLayer.EventAggregate.Event;
import java.util.List;

public interface IEventRepository {
    Event getById(int eventId);
    List<Event> getAll();
}
