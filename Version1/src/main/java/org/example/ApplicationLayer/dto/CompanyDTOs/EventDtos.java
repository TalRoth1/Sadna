package org.example.ApplicationLayer.dto.CompanyDTOs;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.TicketStatus;


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

    /**
     * UC 2.1 (catalog child) and UC 2.3.1 / UC 2.3.2 (search result row).
     *
     * Carries everything the Event Search card needs to render without a
     * follow-up call: company name + rating (for the "Production: X" line
     * and the company-rating filter readout), the price range across the
     * event's areas, and the ticket inventory counts.
     */
    public record EventSummaryDto(
            UUID eventId,
            UUID companyId,
            String companyName,
            double companyRating,
            String name,
            String artist,
            String eventType,
            LocalDateTime date,
            String location,
            double rating,
            double priceMin,
            double priceMax,
            int availableTickets,
            int totalTickets) {
    }

    /**
     * Area summary for the UC 2.1 extended view ("STANDING" or "SITTING").
     * Includes the ticket-id list so the ticket purchase screen can map each
     * ticket back to its zone without a follow-up call.
     */
    public record AreaSummaryDto(
            UUID areaId,
            String kind,
            double price,
            List<UUID> ticketIds) {
    }

    /**
     * Ticket row for the event-details payload. Row/seat are only populated
     * for sitting tickets; standing tickets leave them null.
     */
    public record TicketDetailsDto(
            UUID ticketId,
            UUID areaId,
            TicketStatus status,
            double price,
            Integer row,
            Integer seat) {
    }

    // ---------------------------------------------------------------------
    // Structured purchase / discount policies (gérsion 2 appendix).
    //
    // The domain layer stores rules as a class hierarchy (AgeRule, MinTicketRule,
    // MaxTicketRule, LoneSeatRule joined by PurchaseComposite; OvertDiscount,
    // ConditionalDiscount, CouponCode). These DTOs flatten the rule tree into
    // a list keyed by `kind` so the frontend can render each rule without
    // reflecting on Java types.
    // ---------------------------------------------------------------------

    public record PurchaseRuleDto(
            UUID id,
            String kind,            // "AGE" | "MIN_TICKETS" | "MAX_TICKETS" | "LONE_SEAT"
            Float minAge,           // AGE
            Integer minTickets,     // MIN_TICKETS
            Integer maxTickets,     // MAX_TICKETS
            Boolean allowLoneSeat   // LONE_SEAT
    ) {
    }

    public record PurchasePolicyDto(List<PurchaseRuleDto> rules) {
    }

    public record DiscountRuleDto(
            UUID id,
            String kind,            // "OVERT" | "CONDITIONAL" | "COUPON"
            LocalDate fromDate,
            LocalDate toDate,
            Float percent,
            Integer requiredTickets, // CONDITIONAL only
            Integer appliedTickets,  // CONDITIONAL only
            String code              // COUPON only
    ) {
    }

    public record DiscountPolicyDto(List<DiscountRuleDto> rules) {
    }

    /**
     * UC 2.1 extended event view (selected event details).
     *
     * Carries everything the Event Details page needs in one round-trip:
     * the company line, the status banner, tags, the area/ticket layout
     * for the purchase screen, the aggregated price/inventory counts, and
     * the structured purchase + discount policies.
     */
    public record EventDetailsDto(
            UUID eventId,
            UUID companyId,
            String companyName,
            double companyRating,
            String name,
            String artist,
            String eventType,
            LocalDateTime date,
            String location,
            String description,
            List<String> tags,
            EventStatus status,
            double rating,
            String lotteryId,
            boolean lotteryWinnersDrawn,
            double priceMin,
            double priceMax,
            int availableTickets,
            int totalTickets,
            List<AreaSummaryDto> areas,
            List<TicketDetailsDto> tickets,
            PurchasePolicyDto purchasePolicy,
            DiscountPolicyDto discountPolicy) {
    }
}
