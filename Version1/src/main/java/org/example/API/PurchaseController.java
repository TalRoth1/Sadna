package org.example.API;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.example.ApplicationLayer.PurchaseService;
import org.example.ApplicationLayer.dto.ApiResponse;
import org.example.ApplicationLayer.dto.PurchaseDTOs.ActivePurchaseDTO;
import org.example.ApplicationLayer.dto.PurchaseDTOs.CompletePurchaseRequest;
import org.example.ApplicationLayer.dto.PurchaseDTOs.LotteryDrawRequest;
import org.example.ApplicationLayer.dto.PurchaseDTOs.LotteryRegisterRequest;
import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;
import org.example.ApplicationLayer.dto.PurchaseDTOs.SelectSittingRequest;
import org.example.ApplicationLayer.dto.PurchaseDTOs.SelectStandingRequest;
import org.example.ApplicationLayer.dto.PurchaseDTOs.SelectionAccessDTO;
import org.example.ApplicationLayer.dto.PurchaseDTOs.SelectionAccessRequest;
import org.example.ApplicationLayer.dto.PurchaseDTOs.UpdateSittingRequest;
import org.example.ApplicationLayer.dto.PurchaseDTOs.UpdateStandingRequest;
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
 * PurchaseController
 *
 * Every endpoint returns ResponseEntity<ApiResponse<T>>:
 *   { "success": boolean, "message": string, "data": T | null }
 *
 * Exception → HTTP status mapping:
 *   IllegalArgumentException   → 400 Bad Request   (validation / auth failure in Service)
 *   IllegalStateException      → 400 Bad Request   (domain operation failed; user in queue)
 *   NoSuchElementException     → 404 Not Found     (resource doesn't exist)
 *   Anything else              → 500 Internal Server Error
 */
@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    // ================================================================
    //  1. Ticket selection (creates active purchase)
    // ================================================================

    @PostMapping("/events/{eventId}/selection-access")
    public ResponseEntity<ApiResponse<SelectionAccessDTO>> requestSelectionAccess(
            @PathVariable("eventId") UUID eventId,
            @RequestBody SelectionAccessRequest request) {
        try {
            SelectionAccessDTO access = purchaseService.requestSelectionAccess(
                    request.userId,
                    eventId
            );

            return ResponseEntity.ok(ApiResponse.success(access.message, access));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to request selection access: system exception"));
        }
    }

    @GetMapping("/events/{eventId}/selection-access")
    public ResponseEntity<ApiResponse<SelectionAccessDTO>> getSelectionAccessStatus(
            @PathVariable("eventId") UUID eventId,
            @RequestParam("userId") UUID userId) {
        try {
            SelectionAccessDTO access = purchaseService.getSelectionAccessStatus(
                    userId,
                    eventId
            );

            return ResponseEntity.ok(ApiResponse.success(access.message, access));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch selection access status: system exception"));
        }
    }

    // TODO (V3): Extract userID from JWT token
    @PostMapping("/events/{eventId}/sitting")
    public ResponseEntity<ApiResponse<ActivePurchaseDTO>> selectSittingTickets(
            @PathVariable("eventId") UUID eventId,
            @RequestBody SelectSittingRequest request) {
        try {
            ActivePurchaseDTO activePurchase;
            if (request.accessCode != null && !request.accessCode.isBlank()) {
                activePurchase = purchaseService.selectSittingTicketsWithLotteryCode(
                        eventId, request.ticketIDs, request.userID, request.isConfirmedAge, request.accessCode
                );
            } else {
                activePurchase = purchaseService.selectSittingTickets(
                        eventId, request.ticketIDs, request.userID, request.isConfirmedAge);
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Sitting tickets selected successfully", activePurchase));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to select sitting tickets: system exception"));
        }
    }

    @GetMapping("/users/{userId}/active")
    public ResponseEntity<ApiResponse<List<ActivePurchaseDTO>>> viewActivePurchasesForUser(
            @PathVariable("userId") UUID userId) {
        try {
            List<ActivePurchaseDTO> activePurchases =
                    purchaseService.viewActivePurchasesForUser(userId);

            return ResponseEntity.ok(
                    ApiResponse.success("Active purchases fetched", activePurchases)
            );
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch active purchases: system exception"));
        }
    }

    // TODO (V3): Extract userID from JWT token
    @PostMapping("/events/{eventId}/standing")
    public ResponseEntity<ApiResponse<ActivePurchaseDTO>> selectStandingTickets(
            @PathVariable("eventId") UUID eventId,
            @RequestBody SelectStandingRequest request) {
        try {
            ActivePurchaseDTO activePurchase;
            if (request.accessCode != null && !request.accessCode.isBlank()) {
                activePurchase = purchaseService.selectStandingTicketsWithLotteryCode(
                        eventId, request.amount, request.areaID, request.userID, request.isConfirmedAge, request.accessCode
                );
            } else {
                activePurchase = purchaseService.selectStandingTickets(
                        eventId, request.amount, request.areaID, request.userID, request.isConfirmedAge);
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Standing tickets selected successfully", activePurchase));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to select standing tickets: system exception"));
        }
    }

    // ================================================================
    //  2. Active purchase management
    // ================================================================

    @GetMapping("/active/{activePurchaseId}")
    public ResponseEntity<ApiResponse<ActivePurchaseDTO>> viewActivePurchase(
            @PathVariable("activePurchaseId") UUID activePurchaseId) {
        try {
            ActivePurchaseDTO activePurchase = purchaseService.viewActivePurchase(activePurchaseId);
            return ResponseEntity.ok(ApiResponse.success("Active purchase fetched", activePurchase));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch active purchase: system exception"));
        }
    }

    /**
     * Resume-lookup for the TicketPurchase page. The browser calls this
     * on mount with its cached userId and the event whose page it's about
     * to render. The body is always 200 OK with the standard ApiResponse
     * envelope; {@code data} is the active purchase if one is in flight,
     * or {@code null} if the user has nothing reserved for this event.
     *
     * TODO (V3): replace the userId query param with the JWT subject.
     */
    @GetMapping("/events/{eventId}/active")
    public ResponseEntity<ApiResponse<ActivePurchaseDTO>> viewActivePurchaseForEvent(
            @PathVariable("eventId") UUID eventId,
            @RequestParam("userId") UUID userId) {
        try {
            ActivePurchaseDTO activePurchase =
                    purchaseService.viewActivePurchaseForEvent(userId, eventId);
            String message = activePurchase == null
                    ? "No active purchase for this event"
                    : "Active purchase fetched";
            return ResponseEntity.ok(ApiResponse.success(message, activePurchase));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch active purchase: system exception"));
        }
    }

    @PostMapping("/active/{activePurchaseId}/complete")
    public ResponseEntity<ApiResponse<Void>> completePurchase(
            @PathVariable("activePurchaseId") UUID activePurchaseId,
            @RequestBody CompletePurchaseRequest request) {
        try {
            purchaseService.completePurchase(activePurchaseId, request.paymentDetails, request.couponCode);
            return ResponseEntity.ok(ApiResponse.success("Purchase completed successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to complete purchase: system exception"));
        }
    }

    @DeleteMapping("/active/{activePurchaseId}")
    public ResponseEntity<ApiResponse<Void>> cancelActivePurchase(@PathVariable("activePurchaseId") UUID activePurchaseId) {
        try {
            purchaseService.cancelActivePurchase(activePurchaseId);
            return ResponseEntity.ok(ApiResponse.success("Purchase cancelled successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to cancel purchase: system exception"));
        }
    }

    @PutMapping("/active/{activePurchaseId}/sitting")
    public ResponseEntity<ApiResponse<Void>> updateActivePurchaseSittingTickets(
            @PathVariable("activePurchaseId") UUID activePurchaseId,
            @RequestBody UpdateSittingRequest request) {
        try {
            purchaseService.updateActivePurchaseSittingTickets(activePurchaseId, request.newTicketIds);
            return ResponseEntity.ok(ApiResponse.success("Sitting tickets updated"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update sitting tickets: system exception"));
        }
    }

    @PutMapping("/active/{activePurchaseId}/standing")
    public ResponseEntity<ApiResponse<Void>> updateActivePurchaseStandingTickets(
            @PathVariable("activePurchaseId") UUID activePurchaseId,
            @RequestBody UpdateStandingRequest request) {
        try {
            purchaseService.updateActivePurchaseStandingTickets(activePurchaseId, request.newAmount, request.areaId);
            return ResponseEntity.ok(ApiResponse.success("Standing tickets updated"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update standing tickets: system exception"));
        }
    }

    // ================================================================
    //  3. Lottery
    // ================================================================

    @PostMapping("/events/{eventId}/lottery/register")
    public ResponseEntity<ApiResponse<Void>> registerToLottery(
            @PathVariable("eventId") UUID eventId,
            @RequestBody LotteryRegisterRequest request) {
        try {
            purchaseService.registerToLottery(eventId, request.memberId, request.ticketAmount);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Registered to lottery successfully"));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to register to lottery: system exception"));
        }
    }

    // TODO (V3): Extract caller identity from JWT and verify authorization
    @PostMapping("/events/{eventId}/lottery/draw")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> drawLotteryForEvent(
            @PathVariable("eventId") UUID eventId,
            @RequestBody LotteryDrawRequest request) {
        try {
            java.util.Map<String, String> winnerCodes = purchaseService.drawLotteryForEvent(eventId, request.codeExpiry);
            return ResponseEntity.ok(ApiResponse.success("Lottery drawn successfully", winnerCodes));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to draw lottery: system exception"));
        }
    }

    @GetMapping("/events/{eventId}/lottery/status")
    public ResponseEntity<ApiResponse<org.example.ApplicationLayer.dto.PurchaseDTOs.LotteryStatusDTO>> getLotteryStatus(
            @PathVariable("eventId") UUID eventId,
            @RequestParam("userId") UUID userId) {
        try {
            org.example.ApplicationLayer.dto.PurchaseDTOs.LotteryStatusDTO status =
                    purchaseService.getLotteryStatus(eventId, userId);
            return ResponseEntity.ok(ApiResponse.success("Lottery status fetched", status));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch lottery status: system exception"));
        }
    }

    // ================================================================
    //  4. Purchase history / reports
    // ================================================================

    // TODO (V3): Extract adminId / ownerName / memberId from JWT instead of params



    @GetMapping("/history/members/{memberId}")
    public ResponseEntity<ApiResponse<List<PurchaseHistoryDTO>>> getPurchaseHistoryForMember(
            @PathVariable("memberId") UUID memberId) {
        try {
            List<PurchaseHistoryDTO> history = purchaseService.getPurchaseHistoryForMember(memberId);
            return ResponseEntity.ok(ApiResponse.success("Member history fetched", history));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch member history: system exception"));
        }
    }

    @GetMapping("/events/{eventId}/history/owner")
    public ResponseEntity<ApiResponse<List<PurchaseHistoryDTO>>> getEventPurchaseHistoryForOwner(
            @PathVariable("eventId") UUID eventId,
            @RequestParam String ownerName) {
        try {
            List<PurchaseHistoryDTO> history = purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId);
            return ResponseEntity.ok(ApiResponse.success("Event history for owner fetched", history));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch event history: system exception"));
        }
    }

}