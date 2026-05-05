package org.example.ApplicationLayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;

public class PurchaseService {
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
        if (ticketIDs == null || ticketIDs.isEmpty()) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (userID == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        validateQueueAccess(userID, eventID);

        try {
            purchaseDomainService.selectSittingTickets(eventID, ticketIDs, userID, isConfirmedAge);

            //רק אם נוצר active purchase אז אנחנו מסירים מהמשתמש את ההרשאה לבחור
            queueManager.finishAccess(userID, eventID);
            queueManager.releaseBatch(eventID, 1);

        } catch (DomainException e) {
            throw new IllegalStateException("couldn't select the sitting tickts");
        }
    }

    public void selectStandingTickets(UUID eventID, int amount, UUID areaID, UUID userID, boolean isConfirmedAge)
    {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        if (userID == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        validateQueueAccess(userID, eventID);

        try
        {
            purchaseDomainService.selectStandingTickets(eventID, amount, userID, areaID, isConfirmedAge);

            //רק אם נוצר active purchase אז אנחנו מסירים מהמשתמש את ההרשאה לבחור
            queueManager.finishAccess(userID, eventID);
            queueManager.releaseBatch(eventID, 1);
        }
        catch (DomainException e)
        {
            throw new IllegalStateException("couldn't select the standing tickts");
        }
    }

    public void completePurchase(UUID activePurchaseID, PaymentDetails paymentDetails, String couponCode)
    {
        if (activePurchaseID == null) {
            throw new IllegalArgumentException("Active Purchase ID is required");
        }
        else if (paymentDetails == null) {
            throw new IllegalArgumentException("Payment details are required");
        }

        try
        {
            purchaseDomainService.completePurchase(activePurchaseID, paymentDetails, couponCode);
        }
        catch (DomainException e) {
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

        return purchaseDomainService.getPurchaseHistoryForMember(memberId);
    }
    public ActivePurchase viewActivePurchase(UUID activePurchaseId)
    {
        if (activePurchaseId == null) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        try
        {
            return purchaseDomainService.viewActivePurchase(activePurchaseId);
        }
        catch (DomainException e)
        {
            return null;
        }
    }
    public void cancelActivePurchase(UUID activePurchaseId)
    {
        if (activePurchaseId == null) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        try
        {
            purchaseDomainService.cancelActivePurchase(activePurchaseId);
        }
        catch (DomainException e)
        {
            throw new IllegalStateException("Couldn't cancel purchase");
        }
    }
    public void updateActivePurchaseSittingTickets(UUID activePurchaseId, List<UUID> newTicketIds)
    {
        if (activePurchaseId == null) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }
        if (newTicketIds == null || newTicketIds.isEmpty()) {
            throw new IllegalArgumentException("New ticket IDs are required");
        }
        try
        {
            purchaseDomainService.updateActivePurchaseSittingTickets(activePurchaseId, newTicketIds);
        }
        catch (DomainException e)
        {
            throw new IllegalStateException("Couldn't update active purchase");
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
            throw new IllegalStateException("Couldn't update active purchase");
        }
    }

    public List<PurchaseHistory> getEventPurchaseHistoryForOwner(String ownerName, UUID eventId) {
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

        return purchaseDomainService.getHistoryByEvent(eventId);
    }
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
}
