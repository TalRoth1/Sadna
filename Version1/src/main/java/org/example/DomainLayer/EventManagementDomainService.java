package org.example.DomainLayer;

import java.time.LocalDateTime;
import java.util.List;

import org.example.DomainLayer.EventAggregate.Area;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SeatRowSpec;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.SittingTicket;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.EventAggregate.StandingTicket;

/**
 * Domain orchestration for event CRUD, hall layout, and ticket inventory (diagram: {@code EventManagementDomainService}).
 */
public class EventManagementDomainService {

    private final IEventRepository eventRepository;

    public EventManagementDomainService(IEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Event loadEvent(int eventId) {
        Event e = eventRepository.findByID(eventId);
        if (e == null) {
            throw new DomainException("Event not found");
        }
        return e;
    }

    public Event createEvent(int companyId, LocalDateTime date, String location, String artist, String type,
                             EventStatus status, double rating) {
        int id = eventRepository.allocateNextEventId();
        Event event = new Event(id, companyId, date, location, artist, type, status, rating);
        eventRepository.save(event);
        return event;
    }

    public void updateEvent(int eventId, LocalDateTime date, String location, String artist, String type,
                            EventStatus status, double rating) {
        Event e = loadEvent(eventId);
        e.setDate(date);
        e.setLocation(location);
        e.setArtist(artist);
        e.setType(type);
        e.setStatus(status);
        e.setRating(rating);
        eventRepository.save(e);
    }

    public void deleteEvent(int eventId) {
        if (eventRepository.findByID(eventId) == null) {
            throw new DomainException("Event not found");
        }
        eventRepository.delete(eventId);
    }

    /** Sets the venue graphic for the event (map image URI or path). */
    public void setVenueMapImage(int eventId, String mapImageUri) {
        Event e = loadEvent(eventId);
        if (mapImageUri == null || mapImageUri.isBlank()) {
            throw new IllegalArgumentException("mapImageUri required");
        }
        e.getLayout().setMapImage(mapImageUri.trim());
        eventRepository.save(e);
    }

    public void addSittingArea(int eventId, int areaId, double price) {
        Event e = loadEvent(eventId);
        e.getLayout().addArea(new SittingArea(areaId, price));
        eventRepository.save(e);
    }

    public void addStandingArea(int eventId, int areaId, double price) {
        Event e = loadEvent(eventId);
        e.getLayout().addArea(new StandingArea(areaId, price));
        eventRepository.save(e);
    }

    /**
     * Adds numbered seats for each row in a sitting area (creates {@link SittingTicket} per seat).
     */
    public void addSittingRowsWithSeats(int eventId, int areaId, List<SeatRowSpec> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("rows must not be empty");
        }
        Event e = loadEvent(eventId);
        Area area = e.getLayout().requireArea(areaId);
        if (!(area instanceof SittingArea)) {
            throw new DomainException("Area not found");
        }
        for (SeatRowSpec row : rows) {
            for (int n = 1; n <= row.getSeatCount(); n++) {
                int tid = e.allocateTicketId();
                String seatLabel = row.getRowLabel() + "-" + n;
                e.addTicket(new SittingTicket(tid, eventId, areaId, area.getPrice(), seatLabel));
            }
        }
        eventRepository.save(e);
    }

    /** Adds quantity standing tickets to a standing area (ticket price is the area's {@link Area#getPrice()}). */
    public void addStandingTicketQuantity(int eventId, int areaId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        Event e = loadEvent(eventId);
        Area area = e.getLayout().requireArea(areaId);
        if (!(area instanceof StandingArea)) {
            throw new DomainException("אזור העמידה לא קיים או שאינו אזור עמידה");
        }
        for (int i = 0; i < quantity; i++) {
            int tid = e.allocateTicketId();
            e.addTicket(new StandingTicket(tid, eventId, areaId, area.getPrice()));
        }
        eventRepository.save(e);
    }
}
