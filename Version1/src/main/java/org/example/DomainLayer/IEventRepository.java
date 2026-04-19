package org.example.DomainLayer;

import org.example.DomainLayer.EventAggregate.Event;

public interface IEventRepository {
    Event findByID(int eventID);
}
