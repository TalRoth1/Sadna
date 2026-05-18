package org.example.API;

import org.example.ApplicationLayer.PurchaseService;
import org.example.ApplicationLayer.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    // ==========================================
    // 1. התחלת רכישה (שריון כרטיסים)
    // ==========================================

    // TODO (V3): Extract userID from Token
    @PostMapping("/events/{eventId}/sitting")
    public ResponseEntity<PurchaseResponse> selectSittingTickets(@PathVariable UUID eventId, @RequestBody SelectSittingRequest request) {
        try {
            purchaseService.selectSittingTickets(eventId, request.ticketIDs, request.userID, request.isConfirmedAge);
            return ResponseEntity.ok(new PurchaseResponse(true, "Sitting tickets selected successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    // TODO (V3): Extract userID from Token
    @PostMapping("/events/{eventId}/standing")
    public ResponseEntity<PurchaseResponse> selectStandingTickets(@PathVariable UUID eventId, @RequestBody SelectStandingRequest request) {
        try {
            purchaseService.selectStandingTickets(eventId, request.amount, request.areaID, request.userID, request.isConfirmedAge);
            return ResponseEntity.ok(new PurchaseResponse(true, "Standing tickets selected successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    // ==========================================
    // 2. ניהול רכישות פעילות (Active Purchase)
    // ==========================================

    @GetMapping("/active/{activePurchaseId}")
    public ResponseEntity<PurchaseResponse> viewActivePurchase(@PathVariable UUID activePurchaseId) {
        // ה-Service כעת מחזיר אובייקט מסוג ActivePurchaseDTO ולא ישות Domain
        ActivePurchaseDTO activePurchaseDTO = purchaseService.viewActivePurchase(activePurchaseId);
        if (activePurchaseDTO != null) {
            return ResponseEntity.ok(new PurchaseResponse(true, "Active purchase fetched", activePurchaseDTO));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new PurchaseResponse(false, "Active purchase not found", null));
        }
    }

    @PostMapping("/active/{activePurchaseId}/complete")
    public ResponseEntity<PurchaseResponse> completePurchase(@PathVariable UUID activePurchaseId, @RequestBody CompletePurchaseRequest request) {
        try {
            purchaseService.completePurchase(activePurchaseId, request.paymentDetails, request.couponCode);
            return ResponseEntity.ok(new PurchaseResponse(true, "Purchase completed successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    @DeleteMapping("/active/{activePurchaseId}")
    public ResponseEntity<PurchaseResponse> cancelActivePurchase(@PathVariable UUID activePurchaseId) {
        try {
            purchaseService.cancelActivePurchase(activePurchaseId);
            return ResponseEntity.ok(new PurchaseResponse(true, "Purchase cancelled successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    @PutMapping("/active/{activePurchaseId}/sitting")
    public ResponseEntity<PurchaseResponse> updateActivePurchaseSittingTickets(@PathVariable UUID activePurchaseId, @RequestBody UpdateSittingRequest request) {
        try {
            purchaseService.updateActivePurchaseSittingTickets(activePurchaseId, request.newTicketIds);
            return ResponseEntity.ok(new PurchaseResponse(true, "Sitting tickets updated", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    @PutMapping("/active/{activePurchaseId}/standing")
    public ResponseEntity<PurchaseResponse> updateActivePurchaseStandingTickets(@PathVariable UUID activePurchaseId, @RequestBody UpdateStandingRequest request) {
        try {
            purchaseService.updateActivePurchaseStandingTickets(activePurchaseId, request.newAmount, request.areaId);
            return ResponseEntity.ok(new PurchaseResponse(true, "Standing tickets updated", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    // ==========================================
    // 3. לוטרי (הגרלות)
    // ==========================================

    @PostMapping("/events/{eventId}/lottery/register")
    public ResponseEntity<PurchaseResponse> registerToLottery(@PathVariable UUID eventId, @RequestBody LotteryRegisterRequest request) {
        try {
            purchaseService.registerToLottery(eventId, request.memberId, request.ticketAmount);
            return ResponseEntity.ok(new PurchaseResponse(true, "Registered to lottery successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    // TODO (V3): Extract caller identity and verify authorization
    @PostMapping("/events/{eventId}/lottery/draw")
    public ResponseEntity<PurchaseResponse> drawLotteryForEvent(@PathVariable UUID eventId, @RequestBody LotteryDrawRequest request) {
        try {
            purchaseService.drawLotteryForEvent(eventId, request.codeExpiry);
            return ResponseEntity.ok(new PurchaseResponse(true, "Lottery drawn successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    // ==========================================
    // 4. היסטוריית רכישות (Reports / History)
    // ==========================================

    // TODO (V3): Extract adminId/ownerName/memberId from Token instead of RequestParam

    @GetMapping("/history/all")
    public ResponseEntity<PurchaseResponse> getAllHistory(@RequestParam UUID adminId) {
        try {
            // ה-Service מחזיר רשימה של DTOs
            List<PurchaseHistoryDTO> historyDTOs = purchaseService.getAllHistory(adminId);
            return ResponseEntity.ok(new PurchaseResponse(true, "All history fetched", historyDTOs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping("/history/users/{userId}")
    public ResponseEntity<PurchaseResponse> getHistoryByUser(@PathVariable UUID userId, @RequestParam UUID adminId) {
        try {
            List<PurchaseHistoryDTO> historyDTOs = purchaseService.getHistoryByUser(adminId, userId);
            return ResponseEntity.ok(new PurchaseResponse(true, "User history fetched", historyDTOs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping("/history/events/{eventId}")
    public ResponseEntity<PurchaseResponse> getHistoryByEvent(@PathVariable UUID eventId, @RequestParam UUID adminId) {
        try {
            List<PurchaseHistoryDTO> historyDTOs = purchaseService.getHistoryByEvent(adminId, eventId);
            return ResponseEntity.ok(new PurchaseResponse(true, "Event history fetched", historyDTOs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping("/history/companies/{companyId}")
    public ResponseEntity<PurchaseResponse> getHistoryByCompany(@PathVariable UUID companyId, @RequestParam UUID adminId) {
        try {
            List<PurchaseHistoryDTO> historyDTOs = purchaseService.getHistoryByCompany(adminId, companyId);
            return ResponseEntity.ok(new PurchaseResponse(true, "Company history fetched", historyDTOs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping("/history/members/{memberId}")
    public ResponseEntity<PurchaseResponse> getPurchaseHistoryForMember(@PathVariable UUID memberId) {
        try {
            List<PurchaseHistoryDTO> historyDTOs = purchaseService.getPurchaseHistoryForMember(memberId);
            return ResponseEntity.ok(new PurchaseResponse(true, "Member history fetched", historyDTOs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping("/events/{eventId}/history/owner")
    public ResponseEntity<PurchaseResponse> getEventPurchaseHistoryForOwner(@PathVariable UUID eventId, @RequestParam String ownerName) {
        try {
            List<PurchaseHistoryDTO> historyDTOs = purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId);
            return ResponseEntity.ok(new PurchaseResponse(true, "Event history for owner fetched", historyDTOs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new PurchaseResponse(false, e.getMessage(), null));
        }
    }
}