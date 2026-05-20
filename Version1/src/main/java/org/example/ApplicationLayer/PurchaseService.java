package org.example.ApplicationLayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.example.ApplicationLayer.dto.PurchaseDTOs.ActivePurchaseDTO;
import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

/**
 * PurchaseService
 *
 * Returns raw payload DTOs (ActivePurchaseDTO, PurchaseHistoryDTO) — not wrapped.
 * The Controller wraps them in ApiResponse<T> before sending to the client.
 *
 * Exception conventions for the Controller to map:
 *   - IllegalArgumentException  → 400 Bad Request (bad input, validation, auth checks)
 *   - IllegalStateException     → 400 Bad Request (domain operation failed,
 *                                  user still in queue, etc.)
 *   - NoSuchElementException    → 404 Not Found (resource missing)
 *
 * All Domain → DTO mapping is centralized in the private mapper methods
 * at the bottom of this class.
 */
public class PurchaseService {

    private final PurchaseDomainService purchaseDomainService;
    private final QueueManager queueManager;

    public PurchaseService(PurchaseDomainService purchaseDomainService, QueueManager queueManager) {
        this.purchaseDomainService = purchaseDomainService;
        this.queueManager = queueManager;
    }

    // ================================================================
    //  PRIVATE HELPERS — validation
    // ================================================================

    private void validateAdmin(UUID adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("Admin ID is required");
        }
        if (!purchaseDomainService.validateAdmin(adminId)) {
            throw new IllegalArgumentException("User is not an admin");
        }
    }

    private void validateQueueAccess(UUID userId, UUID eventId) {
        QueueAccessResult result = queueManager.requestSelectionAccess(userId, eventId);
        if (!result.isAllowed()) {
            throw new IllegalStateException(
                    "User is waiting in queue. Position: "
                            + result.getUserPositionInQueue() + "/" + result.getQueueSize());
        }
    }

    // ================================================================
    //  PUBLIC API — ticket selection
    // ================================================================

    /**
     * Selects sitting tickets and creates an active purchase.
     * Returns the newly created ActivePurchaseDTO so the client can later
     * complete / cancel / update it using the returned activePurchaseId.
     */
    public ActivePurchaseDTO selectSittingTickets(UUID eventID, List<UUID> ticketIDs,
                                                  UUID userID, boolean isConfirmedAge) {
        if (ticketIDs == null || ticketIDs.isEmpty()) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (userID == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        validateQueueAccess(userID, eventID);

        try {
            ActivePurchase created = purchaseDomainService.selectSittingTickets(
                    eventID, ticketIDs, userID, isConfirmedAge);

            // Only release the queue slot if the active purchase was successfully created
            queueManager.finishAccess(userID, eventID);
            queueManager.releaseBatch(eventID, 1);

            return mapToActivePurchaseDTO(created);
        } catch (DomainException e) {
            throw new IllegalStateException("Couldn't select the sitting tickets: " + e.getMessage());
        }
    }

    /**
     * Selects standing tickets and creates an active purchase.
     * Returns the newly created ActivePurchaseDTO.
     */
    public ActivePurchaseDTO selectStandingTickets(UUID eventID, int amount, UUID areaID,
                                                   UUID userID, boolean isConfirmedAge) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (userID == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        validateQueueAccess(userID, eventID);

        try {
            ActivePurchase created = purchaseDomainService.selectStandingTickets(
                    eventID, amount, userID, areaID, isConfirmedAge);

            queueManager.finishAccess(userID, eventID);
            queueManager.releaseBatch(eventID, 1);

            return mapToActivePurchaseDTO(created);
        } catch (DomainException e) {
            throw new IllegalStateException("Couldn't select the standing tickets: " + e.getMessage());
        }
    }

    // ================================================================
    //  PUBLIC API — active purchase management
    // ================================================================

    /**
     * Fetches an active purchase by id.
     * Throws NoSuchElementException if not found (Controller maps to 404).
     */
    public ActivePurchaseDTO viewActivePurchase(UUID activePurchaseId) {
        if (activePurchaseId == null) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        try {
            ActivePurchase domainPurchase = purchaseDomainService.viewActivePurchase(activePurchaseId);
            if (domainPurchase == null) {
                throw new NoSuchElementException("Active purchase not found");
            }
            return mapToActivePurchaseDTO(domainPurchase);
        } catch (DomainException e) {
            // Domain treats "not found" as a DomainException — translate to a clean 404 signal
            throw new NoSuchElementException("Active purchase not found");
        }
    }

    public void completePurchase(UUID activePurchaseID, PaymentDetails paymentDetails, String couponCode) {
        if (activePurchaseID == null) {
            throw new IllegalArgumentException("Active Purchase ID is required");
        }
        if (paymentDetails == null) {
            throw new IllegalArgumentException("Payment details are required");
        }

        try {
            purchaseDomainService.completePurchase(activePurchaseID, paymentDetails, couponCode);
        } catch (DomainException e) {
            throw new IllegalStateException("Couldn't complete purchase: " + e.getMessage());
        }
    }

    public void cancelActivePurchase(UUID activePurchaseId) {
        if (activePurchaseId == null) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        try {
            purchaseDomainService.cancelActivePurchase(activePurchaseId);
        } catch (DomainException e) {
            throw new IllegalStateException("Couldn't cancel purchase: " + e.getMessage());
        }
    }

    public void updateActivePurchaseSittingTickets(UUID activePurchaseId, List<UUID> newTicketIds) {
        if (activePurchaseId == null) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        if (newTicketIds == null || newTicketIds.isEmpty()) {
            throw new IllegalArgumentException("New ticket IDs are required");
        }
        try {
            purchaseDomainService.updateActivePurchaseSittingTickets(activePurchaseId, newTicketIds);
        } catch (DomainException e) {
            throw new IllegalStateException("Couldn't update active purchase: " + e.getMessage());
        }
    }

    public void updateActivePurchaseStandingTickets(UUID activePurchaseId, int newAmount, UUID areaId) {
        if (activePurchaseId == null) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        if (newAmount <= 0) {
            throw new IllegalArgumentException("New amount must be non-negative");
        }
        try {
            purchaseDomainService.updateActivePurchaseStandingTickets(activePurchaseId, newAmount, areaId);
        } catch (DomainException e) {
            throw new IllegalStateException("Couldn't update active purchase: " + e.getMessage());
        }
    }

    // ================================================================
    //  PUBLIC API — lottery
    // ================================================================

    public void registerToLottery(UUID eventId, UUID memberId, int ticketAmount) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }
        if (memberId == null) {
            throw new IllegalArgumentException("Member ID is required");
        }
        if (ticketAmount <= 0) {
            throw new IllegalArgumentException("Ticket amount must be greater than zero");
        }

        try {
            purchaseDomainService.registerToLottery(eventId, memberId, ticketAmount);
        } catch (DomainException e) {
            throw new IllegalStateException("Couldn't register to lottery: " + e.getMessage());
        }
    }

    public void drawLotteryForEvent(UUID eventId, LocalDateTime codeExpiry) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }
        if (codeExpiry == null) {
            throw new IllegalArgumentException("Code expiry is required");
        }

        try {
            purchaseDomainService.drawLotteryForEvent(eventId, codeExpiry);
        } catch (DomainException e) {
            throw new IllegalStateException("Couldn't draw lottery: " + e.getMessage());
        }
    }

    // ================================================================
    //  PUBLIC API — history / reports
    // ================================================================

    public List<PurchaseHistoryDTO> getAllHistory(UUID adminId) {
        validateAdmin(adminId);
        List<PurchaseHistory> domainHistories = purchaseDomainService.getAllHistory();
        return domainHistories.stream()
                .map(this::mapToPurchaseHistoryDTO)
                .toList();
    }

    public List<PurchaseHistoryDTO> getHistoryByUser(UUID adminId, UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        validateAdmin(adminId);

        List<PurchaseHistory> domainHistories = purchaseDomainService.getHistoryByUser(userId);
        return domainHistories.stream()
                .map(this::mapToPurchaseHistoryDTO)
                .toList();
    }

    public List<PurchaseHistoryDTO> getHistoryByFilter(UUID adminId, String filterType, UUID filterId) {
        if (filterType == null || filterType.isBlank()) {
            throw new IllegalArgumentException("Filter type is required");
        }
        return switch (filterType.toLowerCase()) {
            case "user"    -> getHistoryByUser(adminId, filterId);
            case "event"   -> getHistoryByEvent(adminId, filterId);
            case "company" -> getHistoryByCompany(adminId, filterId);
            case "all"     -> getAllHistory(adminId);
            default        -> throw new IllegalArgumentException("Invalid filter type");
        };
    }

    public List<PurchaseHistoryDTO> getHistoryByEvent(UUID adminId, UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }
        validateAdmin(adminId);

        List<PurchaseHistory> domainHistories = purchaseDomainService.getHistoryByEvent(eventId);
        return domainHistories.stream()
                .map(this::mapToPurchaseHistoryDTO)
                .toList();
    }

    public List<PurchaseHistoryDTO> getHistoryByCompany(UUID adminId, UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        validateAdmin(adminId);

        List<PurchaseHistory> domainHistories = purchaseDomainService.getHistoryByCompany(companyId);
        return domainHistories.stream()
                .map(this::mapToPurchaseHistoryDTO)
                .toList();
    }

    public List<PurchaseHistoryDTO> getPurchaseHistoryForMember(UUID memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("Member ID is required");
        }
        if (!purchaseDomainService.memberExists(memberId)) {
            throw new IllegalArgumentException("Member does not exist");
        }
        if (!purchaseDomainService.isMember(memberId)) {
            throw new IllegalArgumentException("User is not a member");
        }
        if (!purchaseDomainService.isMemberLoggedIn(memberId)) {
            throw new IllegalArgumentException("Member is not logged in");
        }

        List<PurchaseHistory> domainHistories = purchaseDomainService.getPurchaseHistoryForMember(memberId);
        return domainHistories.stream()
                .map(this::mapToPurchaseHistoryDTO)
                .toList();
    }

    public List<PurchaseHistoryDTO> getEventPurchaseHistoryForOwner(String ownerName, UUID eventId) {
        if (ownerName == null) {
            throw new IllegalArgumentException("Owner ID is required");
        }
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }
        if (!purchaseDomainService.eventExists(eventId)) {
            throw new IllegalArgumentException("Event does not exist");
        }
        if (!purchaseDomainService.isCompanyOwnerOfEvent(ownerName, eventId)) {
            throw new IllegalArgumentException("User is not authorized to view event purchase history");
        }

        List<PurchaseHistory> domainHistories = purchaseDomainService.getHistoryByEvent(eventId);
        return domainHistories.stream()
                .map(this::mapToPurchaseHistoryDTO)
                .toList();
    }

    // ================================================================
    //  PRIVATE MAPPERS — Domain → DTO
    // ================================================================

    private ActivePurchaseDTO mapToActivePurchaseDTO(ActivePurchase domainPurchase) {
        if (domainPurchase == null) {
            return null;
        }

        ActivePurchaseDTO dto = new ActivePurchaseDTO();
        dto.activePurchaseId = domainPurchase.getActivePurchaseId();
        dto.userId = domainPurchase.getUserID();
        dto.eventId = domainPurchase.getEventID();
        dto.ticketPrices = domainPurchase.getTicketPrices();
        dto.endTime = domainPurchase.getEndTime();
        dto.isGuestConfirmedAge = domainPurchase.getGuestAgeConfirmed();
        dto.coupon = domainPurchase.getCoupon();
        dto.price = domainPurchase.getPrice();
        dto.maxWaitTime = domainPurchase.getMaxWaitTime();
        dto.lastUpdate = domainPurchase.getLastUpdate();
        return dto;
    }

    private PurchaseHistoryDTO mapToPurchaseHistoryDTO(PurchaseHistory domainHistory) {
        if (domainHistory == null) {
            return null;
        }

        PurchaseHistoryDTO dto = new PurchaseHistoryDTO();
        dto.userId = domainHistory.getUserId();
        dto.eventId = domainHistory.getEventId();
        dto.ticketIds = domainHistory.getTicketIds();
        dto.purchaseDate = domainHistory.getPurchaseDate();

        if (domainHistory.getPayment() != null) {
            dto.totalPaid = domainHistory.getPayment().getTotal();
            dto.paymentInfo = domainHistory.getPayment().getPaymentInfo();
        } else {
            dto.totalPaid = 0.0;
            dto.paymentInfo = "N/A";
        }

        return dto;
    }
}