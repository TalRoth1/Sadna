package org.example.API;

import java.util.List;
import java.util.UUID;

import org.example.ApplicationLayer.EventService;
import org.example.ApplicationLayer.dto.ApiResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.CompanyCatalogDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.EventDetailsDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.EventSummaryDto;
import org.example.ApplicationLayer.dto.EventDTOs.AddEventConditionalDiscountRequest;
import org.example.ApplicationLayer.dto.EventDTOs.AddEventCouponRequest;
import org.example.ApplicationLayer.dto.EventDTOs.AddEventOvertDiscountRequest;
import org.example.ApplicationLayer.dto.EventDTOs.AddEventPolicyRuleRequest;
import org.example.ApplicationLayer.dto.EventDTOs.AddSittingTicketsRequest;
import org.example.ApplicationLayer.dto.EventDTOs.AddStandingTicketsRequest;
import org.example.ApplicationLayer.dto.EventDTOs.CreateEventRequest;
import org.example.ApplicationLayer.dto.EventDTOs.DeleteEventPolicyRuleRequest;
import org.example.ApplicationLayer.dto.EventDTOs.EditEventRequest;
import org.example.ApplicationLayer.dto.EventDTOs.EventSearchCriteriaRequest;
import org.example.ApplicationLayer.dto.EventDTOs.RateEventRequest;
import org.example.ApplicationLayer.dto.EventDTOs.RemoveEventDiscountRequest;
import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;
import org.example.DomainLayer.EventAggregate.EventSearchCriteria;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EventController
 *
 * Every endpoint returns ResponseEntity<ApiResponse<T>>:
 *   { "success": boolean, "message": string, "data": T | null }
 *
 * Exception → HTTP status mapping:
 *   IllegalArgumentException   → 400 Bad Request
 *   IllegalStateException      → 400 Bad Request
 *   Anything else              → 500 Internal Server Error
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    // ================================================================
    //  1. Event lifecycle
    // ================================================================

    @PostMapping
    public ResponseEntity<ApiResponse<EventDetailsDto>> createEvent(@RequestBody CreateEventRequest request) {
        try {
            EventDetailsDto event = eventService.addEvent(
                    UUID.randomUUID(),
                    request.companyId,
                    request.name,
                    request.date,
                    request.location,
                    request.artist,
                    request.type,
                    request.status,
                    request.description,
                    request.ticketPrice,
                    request.availableTickets);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Event created successfully", event));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create event: system exception"));
        }
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventSummaryDto>> editEvent(
            @PathVariable("eventId") UUID eventId,
            @RequestBody EditEventRequest request) {
        try {
            EventSummaryDto event = eventService.editEvent(
                    eventId, request.name, request.date, request.location,
                    request.artist, request.type, request.status);
            return ResponseEntity.ok(ApiResponse.success("Event updated successfully", event));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update event: system exception"));
        }
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<ApiResponse<Void>> deleteEvent(@PathVariable("eventId") UUID eventId) {
        try {
            eventService.deleteEvent(eventId);
            return ResponseEntity.ok(ApiResponse.success("Event deleted successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete event: system exception"));
        }
    }

    // ================================================================
    //  2. Layout & tickets
    // ================================================================

    @PostMapping("/{eventId}/areas/{areaId}/tickets/standing")
    public ResponseEntity<ApiResponse<Void>> addStandingTickets(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("areaId") UUID areaId,
            @RequestBody AddStandingTicketsRequest request) {
        try {
            eventService.addStandingTickets(eventId, areaId, request.count);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Standing tickets added successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to add standing tickets: system exception"));
        }
    }

    @PostMapping("/{eventId}/areas/{areaId}/tickets/sitting")
    public ResponseEntity<ApiResponse<Void>> addSittingTickets(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("areaId") UUID areaId,
            @RequestBody AddSittingTicketsRequest request) {
        try {
            eventService.addSittingTickets(eventId, areaId, request.rows, request.seatsPerRow);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Sitting tickets added successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to add sitting tickets: system exception"));
        }
    }

    // ================================================================
    //  3. Purchase policy (per-event rules)
    // ================================================================

    @PostMapping("/{eventId}/policy")
    public ResponseEntity<ApiResponse<Void>> addPolicyRule(
            @PathVariable("eventId") UUID eventId,
            @RequestBody AddEventPolicyRuleRequest request) {
        try {
            eventService.addPolicyRule(
                    request.username, request.companyId, eventId,
                    request.age, request.minTicket, request.maxTicket, request.allowLoneSeat);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Policy rule added successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to add policy rule: system exception"));
        }
    }

    @DeleteMapping("/{eventId}/policy/{ruleId}")
    public ResponseEntity<ApiResponse<Void>> deletePolicyRule(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("ruleId") UUID ruleId,
            @RequestBody DeleteEventPolicyRuleRequest request) {
        try {
            eventService.deletePolicyRule(
                    request.username,
                    request.companyId,
                    eventId,
                    ruleId);
            return ResponseEntity.ok(ApiResponse.success("Policy rule deleted successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete policy rule: system exception"));
        }
    }

    // ================================================================
    //  4. Discounts
    // ================================================================

    @PostMapping("/{eventId}/discounts/overt")
    public ResponseEntity<ApiResponse<Void>> addOvertDiscount(
            @PathVariable("eventId") UUID eventId,
            @RequestBody AddEventOvertDiscountRequest request) {
        try {
            eventService.addOvertDiscount(
                    request.username, request.companyId, eventId,
                    request.fromDate, request.toDate, request.discountPercent);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Overt discount added successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to add discount: system exception"));
        }
    }

    @PostMapping("/{eventId}/discounts/conditional")
    public ResponseEntity<ApiResponse<Void>> addConditionalDiscount(
            @PathVariable("eventId") UUID eventId,
            @RequestBody AddEventConditionalDiscountRequest request) {
        try {
            eventService.addConditionalDiscount(
                    request.username, request.companyId, eventId,
                    request.fromDate, request.toDate, request.discountPercent,
                    request.requiredTickets, request.appliedTickets);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Conditional discount added successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to add discount: system exception"));
        }
    }

    @PostMapping("/{eventId}/discounts/coupon")
    public ResponseEntity<ApiResponse<Void>> addCouponCode(
            @PathVariable("eventId") UUID eventId,
            @RequestBody AddEventCouponRequest request) {
        try {
            eventService.addCouponCode(
                    request.username, request.companyId, eventId,
                    request.fromDate, request.toDate, request.discountPercent, request.code);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Coupon added successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to add coupon: system exception"));
        }
    }

    @DeleteMapping("/{eventId}/discounts/{discountId}")
    public ResponseEntity<ApiResponse<Void>> removeDiscount(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("discountId") UUID discountId,
            @RequestBody RemoveEventDiscountRequest request) {
        try {
            eventService.removeDiscount(request.username, request.companyId, eventId, discountId);
            return ResponseEntity.ok(ApiResponse.success("Discount removed successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to remove discount: system exception"));
        }
    }

    // ================================================================
    //  5. Rating
    // ================================================================

    @PostMapping("/{eventId}/ratings")
    public ResponseEntity<ApiResponse<Void>> rateEvent(
            @PathVariable("eventId") UUID eventId,
            @RequestBody RateEventRequest request) {
        try {
            eventService.rateEvent(request.userId, eventId, request.rating);
            return ResponseEntity.ok(ApiResponse.success("Rating submitted successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to submit rating: system exception"));
        }
    }

    // ================================================================
    //  6. Public browsing & search (UC 2.3.x)
    // ================================================================

    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<List<CompanyCatalogDto>>> browseCatalog() {
        try {
            List<CompanyCatalogDto> catalog = eventService.browseCatalog();
            return ResponseEntity.ok(ApiResponse.success("Catalog fetched", catalog));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch catalog: system exception"));
        }
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<ApiResponse<EventDetailsDto>> getEventDetails(@PathVariable("eventId") UUID eventId) {
        try {
            EventDetailsDto event = eventService.getEventDetails(eventId);
            return ResponseEntity.ok(ApiResponse.success("Event details fetched", event));
        } catch (org.example.DomainLayer.DomainException e) {
            // "Event not found" is a business rejection, not a server failure —
            // surface it as 404 so the frontend can render its empty state.
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch event details: system exception"));
        }
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<List<EventSummaryDto>>> searchEvents(
            @RequestBody EventSearchCriteriaRequest request) {
        try {
            EventSearchCriteria criteria = toDomainCriteria(request);
            List<EventSummaryDto> results = eventService.searchEvents(criteria);
            return ResponseEntity.ok(ApiResponse.success("Search completed", results));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Search failed: system exception"));
        }
    }

    @PostMapping("/search/companies/{companyId}")
    public ResponseEntity<ApiResponse<List<EventSummaryDto>>> searchEventsByCompany(
            @PathVariable("companyId") UUID companyId,
            @RequestBody EventSearchCriteriaRequest request) {
        try {
            EventSearchCriteria criteria = toDomainCriteria(request);
            List<EventSummaryDto> results = eventService.searchEventsByCompany(companyId, criteria);
            return ResponseEntity.ok(ApiResponse.success("Search completed", results));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Search failed: system exception"));
        }
    }

    // ================================================================
    //  7. Purchase history (carryover endpoint)
    // ================================================================

    @GetMapping("/{eventId}/history/owner")
    public ResponseEntity<ApiResponse<List<PurchaseHistoryDTO>>> getEventPurchaseHistoryForOwner(
            @PathVariable("eventId") UUID eventId,
            @RequestParam String ownerName) {
        try {
            List<PurchaseHistoryDTO> history = eventService.getEventPurchaseHistoryForOwner(ownerName, eventId);
            return ResponseEntity.ok(ApiResponse.success("Event history fetched", history));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch event history: system exception"));
        }
    }

    // ================================================================
    //  Private helpers
    // ================================================================

    /** Convert the flat Request DTO into the Domain's record-based search criteria. */
    private static EventSearchCriteria toDomainCriteria(EventSearchCriteriaRequest r) {
        if (r == null) {
            return EventSearchCriteria.empty();
        }
        return EventService.toDomainCriteria(
                r.text, r.location,
                r.priceMin, r.priceMax,
                r.dateFrom, r.dateTo,
                r.minEventRating, r.minCompanyRating,
                r.companyId);
    }
}