package org.example.ApplicationLayer.dto;

import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.TicketStatus;
package org.example.ApplicationLayer.dto.CompanyDTOs;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;




 // Read/write models for EventService.
public final class EventDtos {
    private EventDtos() {
    }

    public record SeatInfo(UUID ticketId, String rowLabel, int seatNumber) {
    }

    public record StandingAreaInfo(UUID areaId, String name, double price, int ticketQuantity) {
    }

    public record SittingAreaInfo(UUID areaId, String name, double price, List<SeatInfo> seats) {
    }

    public record EventInfoDto(
            UUID id,
            UUID companyId,
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
    public record SittingSeatInventoryView(UUID ticketId, String seatLabel, TicketStatus status) {
    }

    /** Aggregate availability for a standing zone. */
    public record StandingZoneInventoryView(UUID areaId, double price, int availableCount, int totalCount) {
    }

    /** Sitting zone with per-seat state. */
    public record SittingZoneInventoryView(UUID areaId, double price, List<SittingSeatInventoryView> seats) {
    }

    /**
     * Event map image + full ticket inventory snapshot (guest / public view of stock).
     */
    public record EventInventoryView(
            UUID eventId,
            String mapImageUri,
            List<StandingZoneInventoryView> standingZones,
            List<SittingZoneInventoryView> sittingZones) {
    }

    // ---------------------------------------------------------------------
    // UC 2.1 / UC 2.3.1 / UC 2.3.2 read DTOs (guest-facing browse + search)
    // ---------------------------------------------------------------------

    /** UC 2.1 catalog row: an active company with its publicly-visible events. */
    public record CompanyCatalogDto(
            UUID companyId,
            String companyName,
            double companyRating,
            List<EventSummaryDto> events) {
    }

    /** UC 2.1 (catalog child) and UC 2.3.1 / UC 2.3.2 (search result row). */
    public record EventSummaryDto(
            UUID eventId,
            UUID companyId,
            String name,
            String artist,
            String eventType,
            LocalDateTime date,
            String location,
            double rating) {
    }

    /** Area summary for the UC 2.1 extended view ("STANDING" or "SITTING"). */
    public record AreaSummaryDto(
            UUID areaId,
            String kind,
            double price) {
    }

    /** UC 2.1 extended event view (selected event details). */
    public record EventDetailsDto(
            UUID eventId,
            UUID companyId,
            String name,
            String artist,
            String eventType,
            LocalDateTime date,
            String location,
            double rating,
            List<AreaSummaryDto> areas,
            List<String> purchaseRuleNames,
            List<String> discountRuleNames) {
    }
}
