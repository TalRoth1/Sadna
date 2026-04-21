package org.example.ApplicationLayer;

import java.time.LocalDateTime;
import java.util.List;

import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.TicketStatus;

/**
 * Read/write models for {@link EventService} (application layer).
 */
public final class EventDtos {
    private EventDtos() {
    }

    public record SeatInfo(int ticketId, String rowLabel, int seatNumber) {
    }

    public record StandingAreaInfo(int areaId, String name, double price, int ticketQuantity) {
    }

    public record SittingAreaInfo(int areaId, String name, double price, List<SeatInfo> seats) {
    }

    public record EventInfoDto(
            int id,
            int companyId,
            LocalDateTime eventDate,
            String location,
            List<String> tags,
            EventStatus status,
            String artist,
            String eventType,
            double rating,
            List<StandingAreaInfo> standingAreas,
            List<SittingAreaInfo> sittingAreas,
            int totalTicketCount) {
    }

    /** One seat in inventory (sitting). */
    public record SittingSeatInventoryView(int ticketId, String seatLabel, TicketStatus status) {
    }

    /** Aggregate availability for a standing zone. */
    public record StandingZoneInventoryView(int areaId, double price, int availableCount, int totalCount) {
    }

    /** Sitting zone with per-seat state. */
    public record SittingZoneInventoryView(int areaId, double price, List<SittingSeatInventoryView> seats) {
    }

    /**
     * Event map image + full ticket inventory snapshot (guest / public view of stock).
     */
    public record EventInventoryView(
            int eventId,
            String mapImageUri,
            List<StandingZoneInventoryView> standingZones,
            List<SittingZoneInventoryView> sittingZones) {
    }
}
