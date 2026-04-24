package org.example.ApplicationLayer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.EventAggregate.Area;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SeatRowSpec;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.SittingTicket;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.EventAggregate.Ticket;
import org.example.DomainLayer.EventAggregate.TicketStatus;

import org.example.ApplicationLayer.EventDtos.EventInventoryView;
import org.example.ApplicationLayer.EventDtos.SittingSeatInventoryView;
import org.example.ApplicationLayer.EventDtos.SittingZoneInventoryView;
import org.example.ApplicationLayer.EventDtos.StandingZoneInventoryView;

/**
 * Application façade for event use cases (diagram: {@code EventService} → {@link EventManagementDomainService}).
 */
public class EventService {

    private final EventManagementDomainService eventManagement;

    public EventService(EventManagementDomainService eventManagement) {
        this.eventManagement = eventManagement;
    }

    /** View venue map reference + current inventory (standing totals, sitting per seat). */
    public EventInventoryView getEventInventoryAndMap(int eventId) {
        return buildInventoryView(eventManagement.loadEvent(eventId));
    }

    public Event createEvent(int companyId, LocalDateTime date, String location, String artist, String type,
                             EventStatus status, double rating) {
        return eventManagement.createEvent(companyId, date, location, artist, type, status, rating);
    }

    public void updateEvent(int eventId, LocalDateTime date, String location, String artist, String type,
                            EventStatus status, double rating) {
        eventManagement.updateEvent(eventId, date, location, artist, type, status, rating);
    }

    public void deleteEvent(int eventId) {
        eventManagement.deleteEvent(eventId);
    }

    public void setVenueMapImage(int eventId, String mapImageUri) {
        eventManagement.setVenueMapImage(eventId, mapImageUri);
    }

    public void addSittingArea(int eventId, int areaId, double price) {
        eventManagement.addSittingArea(eventId, areaId, price);
    }

    public void addStandingArea(int eventId, int areaId, double price) {
        eventManagement.addStandingArea(eventId, areaId, price);
    }

    public void addSittingRowsWithSeats(int eventId, int areaId, List<SeatRowSpec> rows) {
        eventManagement.addSittingRowsWithSeats(eventId, areaId, rows);
    }

    public void addStandingTicketQuantity(int eventId, int areaId, int quantity) {
        eventManagement.addStandingTicketQuantity(eventId, areaId, quantity);
    }

    private static EventInventoryView buildInventoryView(Event event) {
        List<StandingZoneInventoryView> standing = new ArrayList<>();
        List<SittingZoneInventoryView> sitting = new ArrayList<>();
        for (Area area : event.getLayout().getAreasView()) {
            if (area instanceof StandingArea) {
                standing.add(buildStandingZone(event, area));
            } else if (area instanceof SittingArea) {
                sitting.add(buildSittingZone(event, area));
            }
        }
        return new EventInventoryView(
                event.getEventId(),
                event.getLayout().getMapImage(),
                List.copyOf(standing),
                List.copyOf(sitting));
    }

    private static StandingZoneInventoryView buildStandingZone(Event event, Area area) {
        int total = 0;
        int available = 0;
        for (int tid : area.getTicketIdsView()) {
            Ticket t = event.getTicketsView().get(tid);
            if (t != null) {
                total++;
                if (t.getStatus() == TicketStatus.AVAILABLE) {
                    available++;
                }
            }
        }
        return new StandingZoneInventoryView(area.getAreaId(), area.getPrice(), available, total);
    }

    private static SittingZoneInventoryView buildSittingZone(Event event, Area area) {
        List<SittingSeatInventoryView> seats = new ArrayList<>();
        for (int tid : area.getTicketIdsView()) {
            Ticket t = event.getTicketsView().get(tid);
            if (t instanceof SittingTicket st) {
                seats.add(new SittingSeatInventoryView(tid, st.getSeatNumber(), st.getStatus()));
            }
        }
        return new SittingZoneInventoryView(area.getAreaId(), area.getPrice(), List.copyOf(seats));
    }
}
