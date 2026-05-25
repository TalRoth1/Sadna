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
     */
    public record AreaSummaryDto(
            UUID areaId,
            String kind,
            double price,
            List<UUID> ticketIds) {
    }

    /**
     * Ticket row for the event-details payload.
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
    // Structured purchase / discount policies
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
            String kind,             // "OVERT" | "CONDITIONAL" | "COUPON"
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
     * UC 2.1 extended event view.
     *
     * discountPolicy:
     * The event's own discount policy only.
     *
     * effectiveDiscountPolicy:
     * The policy the buyer should actually see/pay with:
     * - event discount policy, if the event has discount rules
     * - otherwise company discount policy
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
            DiscountPolicyDto discountPolicy,
            DiscountPolicyDto effectiveDiscountPolicy) {
    }
}