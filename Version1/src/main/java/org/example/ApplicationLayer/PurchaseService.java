package org.example.ApplicationLayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.ApplicationLayer.dto.PurchaseDTOs.ActivePurchaseDTO;
import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.Events.LotteryWonEvent;
import org.example.DomainLayer.Events.PurchaseCompletedEvent;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

public class PurchaseService {
    private static final Logger logger = Logger.getLogger(PurchaseService.class.getName());

    private final PurchaseDomainService purchaseDomainService;
    private final EventPublisher eventPublisher;
    private final QueueManager queueManager;

    public PurchaseService(PurchaseDomainService purchaseDomainService, EventPublisher eventPublisher, QueueManager queueManager) {
        this.purchaseDomainService = purchaseDomainService;
        this.eventPublisher = eventPublisher;
        this.queueManager = queueManager;
    }

    private void validateAdmin(UUID adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("Admin ID is required");
        }
        if (!purchaseDomainService.validateAdmin(adminId)) {
            throw new IllegalArgumentException("User is not an admin");
        }
    }

    public void selectSittingTicketsWithLotteryCode(UUID eventID, List<UUID> ticketIDs, UUID userID, boolean isConfirmedAge, String accessCode) {
        logger.info("caller=" + userID + ", action=selectSittingTicketsWithLotteryCode, target=PurchaseDomainService.selectSittingTicketsWithLotteryCode, params={eventID=" + eventID + ", ticketCount=" + (ticketIDs == null ? 0 : ticketIDs.size()) + ", isConfirmedAge=" + isConfirmedAge + ", accessCodeProvided=" + (accessCode != null && !accessCode.isBlank()) + "}");

        if (eventID == null) {
            logger.warning("action=selectSittingTicketsWithLotteryCode rejected, reason=eventID is null, caller=" + userID);
            throw new IllegalArgumentException("Event ID is required");
        }

        if (ticketIDs == null || ticketIDs.isEmpty()) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (userID == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (accessCode == null || accessCode.isBlank()) {
            throw new IllegalArgumentException("Lottery access code is required");
        }

        try {
            purchaseDomainService.validateSelectionEligibility(eventID, userID, accessCode);

            validateQueueAccess(userID, eventID);

            purchaseDomainService.selectSittingTicketsWithLotteryCode(
                    eventID, ticketIDs, userID, isConfirmedAge, accessCode
            );

            queueManager.finishAccess(userID, eventID);
            queueManager.releaseBatch(eventID, 1);

            logger.info("action=selectSittingTicketsWithLotteryCode completed successfully, caller=" + userID + ", params={eventID=" + eventID + ", ticketCount=" + ticketIDs.size() + "}");
        } catch (DomainException e) {
            logger.severe("action=selectSittingTicketsWithLotteryCode failed, caller=" + userID + ", params={eventID=" + eventID + ", ticketCount=" + (ticketIDs == null ? 0 : ticketIDs.size()) + "}, error=" + e.getMessage());
            throw new IllegalStateException("Couldn't select lottery sitting tickets: " + e.getMessage());
        }
    }

    public void selectStandingTicketsWithLotteryCode(UUID eventID, int amount, UUID areaID, UUID userID, boolean isConfirmedAge, String accessCode) {
        logger.info("caller=" + userID + ", action=selectStandingTicketsWithLotteryCode, target=PurchaseDomainService.selectStandingTicketsWithLotteryCode, params={eventID=" + eventID + ", areaID=" + areaID + ", amount=" + amount + ", isConfirmedAge=" + isConfirmedAge + ", accessCodeProvided=" + (accessCode != null && !accessCode.isBlank()) + "}");

        if (eventID == null) {
            logger.warning("action=selectStandingTicketsWithLotteryCode rejected, reason=missing access code, caller=" + userID + ", eventID=" + eventID);
            throw new IllegalArgumentException("Event ID is required");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        if (areaID == null) {
            throw new IllegalArgumentException("Area ID is required");
        }

        if (userID == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (accessCode == null || accessCode.isBlank()) {
            throw new IllegalArgumentException("Lottery access code is required");
        }

        try {
            purchaseDomainService.validateSelectionEligibility(eventID, userID, accessCode);

            validateQueueAccess(userID, eventID);

            purchaseDomainService.selectStandingTicketsWithLotteryCode(
                    eventID, amount, userID, areaID, isConfirmedAge, accessCode
            );

            queueManager.finishAccess(userID, eventID);
            queueManager.releaseBatch(eventID, 1);

            logger.info("action=selectStandingTicketsWithLotteryCode completed successfully, caller=" + userID + ", params={eventID=" + eventID + ", areaID=" + areaID + ", amount=" + amount + "}");
        } catch (DomainException e) {
            logger.severe("action=selectStandingTicketsWithLotteryCode failed, caller=" + userID + ", params={eventID=" + eventID + ", areaID=" + areaID + ", amount=" + amount + "}, error=" + e.getMessage());
            throw new IllegalStateException("Couldn't select lottery standing tickets: " + e.getMessage());
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

    public ActivePurchaseDTO selectSittingTickets(UUID eventID, List<UUID> ticketIDs, UUID userID, boolean isConfirmedAge) {
        if (ticketIDs == null || ticketIDs.isEmpty()) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (userID == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (purchaseDomainService.isLotteryEvent(eventID)) {
            throw new IllegalStateException("זה אירוע המיועד להגרלה. אי אפשר לקנות ממנו כרטיסים באופן רגיל.");
        }

        validateQueueAccess(userID, eventID);

        try {
            ActivePurchase created = purchaseDomainService.selectSittingTickets(
                    eventID, ticketIDs, userID, isConfirmedAge);

            queueManager.finishAccess(userID, eventID);
            queueManager.releaseBatch(eventID, 1);

            return mapToActivePurchaseDTO(created);
        } catch (DomainException e) {
            throw new IllegalStateException("Couldn't select the sitting tickets: " + e.getMessage());
        }
    }

    public ActivePurchaseDTO selectStandingTickets(UUID eventID, int amount, UUID areaID, UUID userID, boolean isConfirmedAge) {
        if (amount <= 0) {
            logger.warning("Standing tickets selection failed: amount must be positive. User: " + userID);
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (userID == null) {
            logger.warning("Standing tickets selection failed: userID is null");
            throw new IllegalArgumentException("User ID is required");
        }

        if (purchaseDomainService.isLotteryEvent(eventID)) {
            logger.warning("Regular purchase blocked: Event " + eventID + " is restricted to lottery winners.");
            throw new IllegalStateException("זה אירוע המיועד להגרלה. אי אפשר לקנות ממנו כרטיסים באופן רגיל.");
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
            throw new NoSuchElementException("Active purchase not found");
        }
    }

    public void completePurchase(UUID activePurchaseID, PaymentDetails paymentDetails, String couponCode) {
        if (activePurchaseID == null) {
            logger.warning("Purchase completion failed: activePurchaseID is null");
            throw new IllegalArgumentException("Active Purchase ID is required");
        }
        if (paymentDetails == null) {
            throw new IllegalArgumentException("Payment details are required");
        }

        try {
            ActivePurchase activePurchase = purchaseDomainService.viewActivePurchase(activePurchaseID);
            UUID userId = activePurchase.getUserID();

            purchaseDomainService.completePurchase(activePurchaseID, paymentDetails, couponCode);

            logger.info("Purchase completed successfully for activePurchaseID: " + activePurchaseID);
            eventPublisher.publish(new PurchaseCompletedEvent(userId));
        } catch (DomainException e) {
            logger.severe("Critical failure in completePurchase for ID " + activePurchaseID + ". Reason: " + e.getMessage());
            throw new IllegalStateException("Couldn't complete purchase: " + e.getMessage());
        }
    }

    public void cancelActivePurchase(UUID activePurchaseId) {
        if (activePurchaseId == null) {
            logger.warning("viewActivePurchase failed: provided activePurchaseId is null");
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
            logger.warning("Update failed: activePurchaseId is null");
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        if (newTicketIds == null || newTicketIds.isEmpty()) {
            logger.warning("Update failed: newTicketIds is null or empty for purchase: " + activePurchaseId);
            throw new IllegalArgumentException("New ticket IDs are required");
        }
        try {
            purchaseDomainService.updateActivePurchaseSittingTickets(activePurchaseId, newTicketIds);
        } catch (DomainException e) {
            throw new IllegalStateException("Couldn't update active purchase: " + e.getMessage());
        }
    }

    public void updateActivePurchaseStandingTickets(UUID activePurchaseId, int newAmount, UUID areaId) {
        logger.info("Starting updateActivePurchaseStandingTickets: activePurchaseId=" + activePurchaseId + ", newAmount=" + newAmount + ", areaId=" + areaId);

        if (activePurchaseId == null) {
            logger.warning("Update failed: activePurchaseId is null");
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        if (newAmount <= 0) {
            logger.warning("Update failed: newAmount must be positive. Received: " + newAmount);
            throw new IllegalArgumentException("New amount must be non-negative");
        }
        try {
            purchaseDomainService.updateActivePurchaseStandingTickets(activePurchaseId, newAmount, areaId);
            logger.info("Successfully updated standing tickets for active purchase: " + activePurchaseId);
        } catch (DomainException e) {
            throw new IllegalStateException("Couldn't update active purchase: " + e.getMessage());
        }
    }

    public void registerToLottery(UUID eventId, UUID memberId, int ticketAmount) {
        logger.info("caller=" + memberId + ", action=registerToLottery, target=PurchaseDomainService.registerToLottery, params={eventId=" + eventId + ", memberId=" + memberId + ", ticketAmount=" + ticketAmount + "}");
        if (eventId == null) {
            logger.warning("action=registerToLottery rejected, reason=eventId is null, caller=" + memberId);
            throw new IllegalArgumentException("Event ID is required");
        }
        if (memberId == null) {
            logger.warning("action=registerToLottery rejected, reason=memberId is null");
            throw new IllegalArgumentException("Member ID is required");
        }
        if (ticketAmount <= 0) {
            logger.warning("action=registerToLottery rejected, reason=invalid ticketAmount, caller=" + memberId + ", ticketAmount=" + ticketAmount);
            throw new IllegalArgumentException("Ticket amount must be greater than zero");
        }

        try {
            purchaseDomainService.registerToLottery(eventId, memberId, ticketAmount);
            logger.info("action=registerToLottery completed successfully, params={eventId=" + eventId + ", memberId=" + memberId + ", ticketAmount=" + ticketAmount + "}");
        } catch (DomainException e) {
            logger.severe("action=registerToLottery failed, caller=" + memberId + ", target=PurchaseDomainService.registerToLottery, params={eventId=" + eventId + ", memberId=" + memberId + ", ticketAmount=" + ticketAmount + "}, error=" + e.getMessage());
            throw new IllegalStateException("Couldn't register to lottery: " + e.getMessage());
        }
    }

    public void drawLotteryForEvent(UUID eventId, LocalDateTime codeExpiry) {
        logger.info("caller=system/admin"
                + ", action=drawLotteryForEvent"
                + ", target=PurchaseDomainService.drawLotteryForEvent"
                + ", params={eventId=" + eventId + ", codeExpiry=" + codeExpiry + "}");

        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }
        if (codeExpiry == null) {
            throw new IllegalArgumentException("Code expiry is required");
        }

        try {
            Map<String, String> winnerCodes =
                    purchaseDomainService.drawLotteryForEvent(eventId, codeExpiry);

            logger.info("action=drawLotteryForEvent completed successfully"
                    + ", params={eventId=" + eventId + ", codeExpiry=" + codeExpiry + "}");

            for (Map.Entry<String, String> entry : winnerCodes.entrySet()) {
                String winnerId = entry.getKey();
                String accessCode = entry.getValue();

                eventPublisher.publish(
                        new LotteryWonEvent(winnerId, eventId, accessCode, codeExpiry)
                );
            }

        } catch (DomainException e) {
            logger.severe("action=drawLotteryForEvent failed"
                    + ", caller=system/admin"
                    + ", target=PurchaseDomainService.drawLotteryForEvent"
                    + ", params={eventId=" + eventId + ", codeExpiry=" + codeExpiry + "}"
                    + ", error=" + e.getMessage());

            throw new IllegalStateException("Couldn't draw lottery: " + e.getMessage());
        }
    }

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
            case "user" -> getHistoryByUser(adminId, filterId);
            case "event" -> getHistoryByEvent(adminId, filterId);
            case "company" -> getHistoryByCompany(adminId, filterId);
            case "all" -> getAllHistory(adminId);
            default -> throw new IllegalArgumentException("Invalid filter type");
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