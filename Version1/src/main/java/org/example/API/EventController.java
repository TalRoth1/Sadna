package org.example.API;

import org.example.ApplicationLayer.EventService;
import org.example.ApplicationLayer.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    // ==========================================
    // 1. ניהול אירועים (הוספה, עריכה, מחיקה)
    // ==========================================

    @PostMapping
    public ResponseEntity<EventResponse> addEvent(@RequestBody AddEventRequest request) {
        try {
            // תיקון 1: יצירת ה-UUID בקונטרולר והתאמה מדויקת לחתימת ה-Service
            UUID newEventId = UUID.randomUUID();
            eventService.addEvent(
                    newEventId,
                    request.companyId,
                    request.eventDate,
                    request.location,
                    request.artist,
                    request.type,
                    request.status
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(new EventResponse(true, "Event created successfully", newEventId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<EventResponse> editEvent(@PathVariable UUID eventId, @RequestBody EditEventRequest request) {
        try {
            // תיקון 1: התאמה לחתימה המדויקת של עריכה
            eventService.editEvent(
                    eventId,
                    request.eventDate,
                    request.location,
                    request.artist,
                    request.type,
                    request.status
            );
            return ResponseEntity.ok(new EventResponse(true, "Event updated successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<EventResponse> deleteEvent(@PathVariable UUID eventId) {
        try {
            eventService.deleteEvent(eventId);
            return ResponseEntity.ok(new EventResponse(true, "Event deleted successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    // ==========================================
    // 2. ניהול כרטיסים ואזורים
    // ==========================================

    @PostMapping("/{eventId}/areas/standing")
    public ResponseEntity<EventResponse> addStandingTickets(@PathVariable UUID eventId, @RequestBody AddStandingAreaRequest request) {
        try {
            eventService.addStandingTickets(eventId, request.areaId, request.count);
            return ResponseEntity.ok(new EventResponse(true, "Standing tickets added", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    @PostMapping("/{eventId}/areas/sitting")
    public ResponseEntity<EventResponse> addSittingTickets(@PathVariable UUID eventId, @RequestBody AddSittingAreaRequest request) {
        try {
            eventService.addSittingTickets(eventId, request.areaId, request.rows, request.seatsPerRow);
            return ResponseEntity.ok(new EventResponse(true, "Sitting tickets added", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    // ==========================================
    // 3. מדיניות והנחות
    // ==========================================

    @PostMapping("/{eventId}/policies")
    public ResponseEntity<EventResponse> addPolicyRule(@PathVariable UUID eventId, @RequestBody EventPolicyRequest request) {
        try {
            eventService.addPolicyRule(request.username, request.companyId, eventId, Optional.ofNullable(request.age), Optional.ofNullable(request.minTicket), Optional.ofNullable(request.maxTicket), Optional.ofNullable(request.allowLoneSeat));
            return ResponseEntity.ok(new EventResponse(true, "Policy rule added to event", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    @DeleteMapping("/{eventId}/policies")
    public ResponseEntity<EventResponse> deletePolicyRule(@PathVariable UUID eventId, @RequestBody EventPolicyRequest request) {
        try {
            eventService.deletePolicyRule(request.username, request.companyId, eventId, request.age != null, request.minTicket != null, request.maxTicket != null, request.allowLoneSeat != null);
            return ResponseEntity.ok(new EventResponse(true, "Policy rule deleted from event", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    @PostMapping("/{eventId}/discounts/overt")
    public ResponseEntity<EventResponse> addOvertDiscount(@PathVariable UUID eventId, @RequestBody EventDiscountRequest request) {
        try {
            eventService.addOvertDiscount(request.username, request.companyId, eventId, request.fromDate, request.toDate, request.discountPercent);
            return ResponseEntity.ok(new EventResponse(true, "Overt discount added to event", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    @PostMapping("/{eventId}/discounts/conditional")
    public ResponseEntity<EventResponse> addConditionalDiscount(@PathVariable UUID eventId, @RequestBody EventDiscountRequest request) {
        try {
            eventService.addConditionalDiscount(request.username, request.companyId, eventId, request.fromDate, request.toDate, request.discountPercent, request.requiredTickets, request.appliedTickets);
            return ResponseEntity.ok(new EventResponse(true, "Conditional discount added to event", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    @PostMapping("/{eventId}/discounts/coupon")
    public ResponseEntity<EventResponse> addCouponCode(@PathVariable UUID eventId, @RequestBody EventDiscountRequest request) {
        try {
            eventService.addCouponCode(request.username, request.companyId, eventId, request.fromDate, request.toDate, request.discountPercent, request.code);
            return ResponseEntity.ok(new EventResponse(true, "Coupon code added to event", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    @DeleteMapping("/{eventId}/discounts/{discountId}")
    public ResponseEntity<EventResponse> removeDiscount(@PathVariable UUID eventId, @PathVariable UUID discountId, @RequestParam String username, @RequestParam UUID companyId) {
        try {
            eventService.removeDiscount(username, companyId, eventId, discountId);
            return ResponseEntity.ok(new EventResponse(true, "Discount removed from event", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    // ==========================================
    // 4. פעולות משתמשים (דירוג, קטלוג, חיפוש)
    // ==========================================

    @PostMapping("/{eventId}/rate")
    public ResponseEntity<EventResponse> rateEvent(@PathVariable UUID eventId, @RequestBody RateEventRequest request) {
        try {
            eventService.rateEvent(request.userId, eventId, request.rating);
            return ResponseEntity.ok(new EventResponse(true, "Event rated successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping("/catalog")
    public ResponseEntity<EventResponse> browseCatalog() {
        try {
            List<CompanyCatalogDto> catalogData = eventService.browseCatalog();
            return ResponseEntity.ok(new EventResponse(true, "Catalog fetched", catalogData));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEventDetails(@PathVariable UUID eventId) {
        try {
            EventDetailsDto eventDetails = eventService.getEventDetails(eventId);
            return ResponseEntity.ok(new EventResponse(true, "Event details fetched", eventDetails));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new EventResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<EventResponse> searchEvents(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) UUID companyId) {
        try {
            // תיקון 2: שימוש ב-Immutable Object (Record/Builder pattern) ליצירת הקריטריונים
            EventSearchCriteria criteria = EventSearchCriteria.empty();

            // במידה וזה Immutable, כל קריאה מחזירה אובייקט חדש לכן אנו דורסים את criteria
            if (query != null) criteria = criteria.withQuery(query);
            if (minPrice != null) criteria = criteria.withMinPrice(minPrice);
            if (maxPrice != null) criteria = criteria.withMaxPrice(maxPrice);
            if (location != null) criteria = criteria.withLocation(location);

            List<EventSummaryDto> searchResults;
            if (companyId != null) {
                searchResults = eventService.searchEventsByCompany(companyId, criteria);
            } else {
                searchResults = eventService.searchEvents(criteria);
            }

            return ResponseEntity.ok(new EventResponse(true, "Search completed", searchResults));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new EventResponse(false, e.getMessage(), null));
        }
    }
}