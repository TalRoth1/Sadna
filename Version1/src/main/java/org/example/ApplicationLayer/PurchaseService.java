package org.example.ApplicationLayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

public class PurchaseService {
    private static final Logger logger = Logger.getLogger(PurchaseService.class.getName());
    private final PurchaseDomainService purchaseDomainService;

    private final QueueManager queueManager;

    public PurchaseService(PurchaseDomainService purchaseDomainService, QueueManager queueManager) {
        this.purchaseDomainService = purchaseDomainService;
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

    private void validateQueueAccess(UUID userId, UUID eventId)
    {
        QueueAccessResult result = queueManager.requestSelectionAccess(userId, eventId);
        if (!result.isAllowed())
            throw new IllegalStateException("User is waiting in queue. Position: " + result.getUserPositionInQueue() + "/" + result.getQueueSize());
    }

    public void selectSittingTickets(UUID eventID, List<UUID> ticketIDs, UUID userID, boolean isConfirmedAge)
    {
        logger.info("Starting selectSittingTickets: eventID=" + eventID + ", ticketIDs=" + ticketIDs + ", userID=" + userID);

        if (ticketIDs == null || ticketIDs.isEmpty())
        {
            logger.warning("Attempted to select tickets with null or empty list for user: " + userID);
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
            purchaseDomainService.selectSittingTickets(eventID, ticketIDs, userID, isConfirmedAge);

            //רק אם נוצר active purchase אז אנחנו מסירים מהמשתמש את ההרשאה לבחור
            queueManager.finishAccess(userID, eventID);
            queueManager.releaseBatch(eventID, 1);

            logger.info("Successfully selected sitting tickets for user: " + userID);

        } catch (DomainException e)
        {
            logger.severe("Failed to select sitting tickets for user " + userID + " on event " + eventID + ". Reason: " + e.getMessage());
            throw new IllegalStateException("couldn't select the sitting tickts");
        }
    }

    public void selectStandingTickets(UUID eventID, int amount, UUID areaID, UUID userID, boolean isConfirmedAge)
    {
        logger.info("Starting selectStandingTickets: eventID=" + eventID + ", amount=" + amount + ", areaID=" + areaID + ", userID=" + userID);

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

        try
        {
            purchaseDomainService.selectStandingTickets(eventID, amount, userID, areaID, isConfirmedAge);

            //רק אם נוצר active purchase אז אנחנו מסירים מהמשתמש את ההרשאה לבחור
            queueManager.finishAccess(userID, eventID);
            queueManager.releaseBatch(eventID, 1);

            logger.info("Successfully selected standing tickets for user: " + userID + " on event: " + eventID);
        }
        catch (DomainException e)
        {
            logger.severe("Failed to select standing tickets for user " + userID + ". Domain Error: " + e.getMessage());
            throw new IllegalStateException("couldn't select the standing tickts");
        }
    }

    public void completePurchase(UUID activePurchaseID, PaymentDetails paymentDetails, String couponCode)
    {
        logger.info("Starting completePurchase: activePurchaseID=" + activePurchaseID +
                ", couponCode=" + (couponCode != null ? couponCode : "none"));

        if (activePurchaseID == null) {
            logger.warning("Purchase completion failed: activePurchaseID is null");
            throw new IllegalArgumentException("Active Purchase ID is required");
        }
        else if (paymentDetails == null) {
            throw new IllegalArgumentException("Payment details are required");
        }

        try
        {
            purchaseDomainService.completePurchase(activePurchaseID, paymentDetails, couponCode);
            logger.info("Purchase completed successfully for activePurchaseID: " + activePurchaseID);
        }
        catch (DomainException e) {
            logger.severe("Critical failure in completePurchase for ID " + activePurchaseID +
                    ". Reason: " + e.getMessage());
            throw new IllegalStateException("couldn't complete purchase");
        }
    }

    public List<PurchaseHistory> getAllHistory(UUID adminId) {
        validateAdmin(adminId);
        return purchaseDomainService.getAllHistory();
    }

    public List<PurchaseHistory> getHistoryByUser(UUID adminId, UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        validateAdmin(adminId);

        return purchaseDomainService.getHistoryByUser(userId);
    }

    public List<PurchaseHistory> getHistoryByFilter(UUID adminId, String filterType, UUID filterId) {
        logger.info("caller=" + adminId
                + ", action=getHistoryByFilter"
                + ", target=PurchaseDomainService"
                + ", params={adminId=" + adminId + ", filterType=" + filterType + ", filterId=" + filterId + "}");

        try {
            if (filterType == null || filterType.isBlank()) {
                throw new IllegalArgumentException("Filter type is required");
            }

            List<PurchaseHistory> result = switch (filterType.toLowerCase()) {
                case "user" -> getHistoryByUser(adminId, filterId);
                case "event" -> getHistoryByEvent(adminId, filterId);
                case "company" -> getHistoryByCompany(adminId, filterId);
                case "all" -> getAllHistory(adminId);
                default -> throw new IllegalArgumentException("Invalid filter type");
            };

            logger.info("action=getHistoryByFilter completed successfully"
                    + ", resultSize=" + result.size()
                    + ", params={adminId=" + adminId + ", filterType=" + filterType + ", filterId=" + filterId + "}");

            return result;

        } catch (RuntimeException e) {
            logger.severe("action=getHistoryByFilter failed"
                    + ", caller=" + adminId
                    + ", target=PurchaseDomainService"
                    + ", params={adminId=" + adminId + ", filterType=" + filterType + ", filterId=" + filterId + "}"
                    + ", error=" + e.getMessage());
            throw e;
        }
    }


    public List<PurchaseHistory> getHistoryByEvent(UUID adminId, UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event ID is required");
        }
        validateAdmin(adminId);
        return purchaseDomainService.getHistoryByEvent(eventId);
    }

    public List<PurchaseHistory> getHistoryByCompany(UUID adminId, UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        validateAdmin(adminId);
        return purchaseDomainService.getHistoryByCompany(companyId);
    }

    public List<PurchaseHistory> getPurchaseHistoryForMember(UUID memberId) {
        logger.info("caller=" + memberId
                + ", action=getPurchaseHistoryForMember"
                + ", target=PurchaseDomainService.getPurchaseHistoryForMember"
                + ", params={memberId=" + memberId + "}");

        try {
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

            List<PurchaseHistory> result = purchaseDomainService.getPurchaseHistoryForMember(memberId);

            logger.info("action=getPurchaseHistoryForMember completed successfully"
                    + ", resultSize=" + result.size()
                    + ", params={memberId=" + memberId + "}");

            return result;

        } catch (RuntimeException e) {
            logger.severe("action=getPurchaseHistoryForMember failed"
                    + ", caller=" + memberId
                    + ", target=PurchaseDomainService.getPurchaseHistoryForMember"
                    + ", params={memberId=" + memberId + "}"
                    + ", error=" + e.getMessage());
            throw e;
        }
    }

    public ActivePurchase viewActivePurchase(UUID activePurchaseId)
    {
        logger.info("Request to view active purchase: ID=" + activePurchaseId);

        if (activePurchaseId == null) {
            logger.warning("viewActivePurchase failed: provided activePurchaseId is null");
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        try
        {
            return purchaseDomainService.viewActivePurchase(activePurchaseId);
        }
        catch (DomainException e)
        {
            logger.severe("Failed to view active purchase " + activePurchaseId + ". Reason: " + e.getMessage());
            return null;
        }
    }
    public void cancelActivePurchase(UUID activePurchaseId)
    {
        logger.info("Attempting to cancel active purchase: ID=" + activePurchaseId);

        if (activePurchaseId == null) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        try
        {
            purchaseDomainService.cancelActivePurchase(activePurchaseId);

            logger.info("Successfully cancelled active purchase: ID=" + activePurchaseId);
        }
        catch (DomainException e)
        {
            logger.severe("Failed to cancel active purchase " + activePurchaseId + ". Reason: " + e.getMessage());
            throw new IllegalStateException("Couldn't cancel purchase");
        }
    }
    public void updateActivePurchaseSittingTickets(UUID activePurchaseId, List<UUID> newTicketIds)
    {
        logger.info("Starting updateActivePurchaseSittingTickets: activePurchaseId=" + activePurchaseId + ", newTicketIds=" + newTicketIds);

        if (activePurchaseId == null) {
            logger.warning("Update failed: activePurchaseId is null");
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        if (newTicketIds == null || newTicketIds.isEmpty()) {
            logger.warning("Update failed: newTicketIds is null or empty for purchase: " + activePurchaseId);
            throw new IllegalArgumentException("New ticket IDs are required");
        }
        try
        {
            purchaseDomainService.updateActivePurchaseSittingTickets(activePurchaseId, newTicketIds);
            logger.info("Successfully updated sitting tickets for active purchase: " + activePurchaseId);
        }
        catch (DomainException e)
        {
            logger.severe("Failed to update sitting tickets for purchase " + activePurchaseId + ". Reason: " + e.getMessage());
            throw new IllegalStateException("Couldn't update active purchase");
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
            logger.severe("Failed to update standing tickets for purchase " + activePurchaseId + ". Reason: " + e.getMessage());
            throw new IllegalStateException("Couldn't update active purchase");
        }
    }

    public List<PurchaseHistory> getEventPurchaseHistoryForOwner(String ownerName, UUID eventId) {
        logger.info("caller=" + ownerName
                + ", action=getEventPurchaseHistoryForOwner"
                + ", target=PurchaseDomainService.getHistoryByEvent"
                + ", params={ownerName=" + ownerName + ", eventId=" + eventId + "}");

        try {
            if (ownerName == null || ownerName.isBlank()) {
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

            List<PurchaseHistory> result = purchaseDomainService.getHistoryByEvent(eventId);

            logger.info("action=getEventPurchaseHistoryForOwner completed successfully"
                    + ", resultSize=" + result.size()
                    + ", params={ownerName=" + ownerName + ", eventId=" + eventId + "}");

            return result;

        } catch (RuntimeException e) {
            logger.severe("action=getEventPurchaseHistoryForOwner failed"
                    + ", caller=" + ownerName
                    + ", target=PurchaseDomainService.getHistoryByEvent"
                    + ", params={ownerName=" + ownerName + ", eventId=" + eventId + "}"
                    + ", error=" + e.getMessage());
            throw e;
        }
    }

    public void registerToLottery(UUID eventId, UUID memberId, int ticketAmount) {
        logger.info("caller=" + memberId
            + ", action=registerToLottery"
            + ", target=PurchaseDomainService.registerToLottery"
            + ", params={eventId=" + eventId + ", memberId=" + memberId + ", ticketAmount=" + ticketAmount + "}");
        
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
            logger.info("action=registerToLottery completed successfully"
                + ", params={eventId=" + eventId + ", memberId=" + memberId + ", ticketAmount=" + ticketAmount + "}");
        } catch (DomainException e) {
            logger.severe("action=registerToLottery failed"
                + ", caller=" + memberId
                + ", target=PurchaseDomainService.registerToLottery"
                + ", params={eventId=" + eventId + ", memberId=" + memberId + ", ticketAmount=" + ticketAmount + "}"
                + ", error=" + e.getMessage());
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
            purchaseDomainService.drawLotteryForEvent(eventId, codeExpiry);
            logger.info("action=drawLotteryForEvent completed successfully"
                + ", params={eventId=" + eventId + ", codeExpiry=" + codeExpiry + "}");
        } catch (DomainException e) {
            logger.severe("action=drawLotteryForEvent failed"
                + ", caller=system/admin"
                + ", target=PurchaseDomainService.drawLotteryForEvent"
                + ", params={eventId=" + eventId + ", codeExpiry=" + codeExpiry + "}"
                + ", error=" + e.getMessage());
            throw new IllegalStateException("Couldn't draw lottery: " + e.getMessage());
        }
    }
}
