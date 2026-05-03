package org.example.ApplicationLayer;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.IEventRepository;
import org.example.InfrastructureLayer.EventRepository;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class EventServiceTest {

    @Test
    public void eventRate_success()
    {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        UUID eventID = UUID.randomUUID();

        Event event = new Event(eventID, UUID.randomUUID(), LocalDateTime.now(), "sdsdsdsd", "sdsdsdsd", "sdsdsdsd", EventStatus.ACTIVE);

        InMemoryEventRepository eventRepository = new InMemoryEventRepository();
        eventRepository.save(event);

        EventManagementDomainService eventManagementDomainService = new EventManagementDomainService(eventRepository, null, null);
        EventService eventService = new EventService(eventManagementDomainService);

        eventService.rateEvent(user1, eventID, 5);
        eventService.rateEvent(user2, eventID, 1);

        assertTrue(3 == event.getRating());
    }
    @Test
    public void eventRate_samePerson_thenItFails()
    {
        UUID user1 = UUID.randomUUID();

        UUID eventID = UUID.randomUUID();

        Event event = new Event(eventID, UUID.randomUUID(), LocalDateTime.now(), "sdsdsdsd", "sdsdsdsd", "sdsdsdsd", EventStatus.ACTIVE);

        InMemoryEventRepository eventRepository = new InMemoryEventRepository();
        eventRepository.save(event);

        EventManagementDomainService eventManagementDomainService = new EventManagementDomainService(eventRepository, null, null);
        EventService eventService = new EventService(eventManagementDomainService);

        eventService.rateEvent(user1, eventID, 5);
        assertThrows(DomainException.class, () -> eventService.rateEvent(user1, eventID, 1));

        assertTrue(5 == event.getRating());
    }

    private static class InMemoryEventRepository implements IEventRepository
    {
        Map<UUID, Event> eventsByID = new LinkedHashMap<>();

        @Override
        public Event getById(UUID eventId) {
            return eventsByID.get(eventId);
        }

        @Override
        public List<Event> getAll() {
            return eventsByID.values().stream().toList();
        }

        @Override
        public void save(Event event) {
            eventsByID.put(event.getEventId(), event);
        }

        @Override
        public void delete(UUID eventId) {
            eventsByID.remove(eventId);
        }
    }
}