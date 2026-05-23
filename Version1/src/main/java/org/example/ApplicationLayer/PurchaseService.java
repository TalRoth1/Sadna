package org.example.ApplicationLayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.Events.LotteryWonEvent;
import org.example.DomainLayer.Events.PurchaseCompletedEvent;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import org.example.ApplicationLayer.dto.PurchaseDTOs.ActivePurchaseDTO;
import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;

@Service
public class PurchaseService {
    private static final Logger logger = Logger.getLogger(PurchaseService.class.getName());
    private final PurchaseDomainService purchaseDomainService;
    private final EventPublisher eventPublisher;
    private final INotifier notifier;

    private final QueueManager queueManager;

    public PurchaseService(PurchaseDomainService purchaseDomainService, EventPublisher eventPublisher, QueueManager queueManager, INotifier notifier) {
        this.purchaseDomainService = purchaseDomainService;
        this.eventPublisher = eventPublisher;
        this.queueManager = queueManager;
        this.notifier = notifier;
    }

    private void validateAdmin(UUID adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("Admin ID is required");
        }
        if (!purchaseDomainService.validateAdmin(adminId)) {
            throw new IllegalArgumentException("User is not an admin");
        }
    }

    public void selectSittingTicketsWithLotteryCode(UUID eventID,List<UUID> ticketIDs,UUID userID,boolean isConfirmedAge,String accessCode)
    {
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
            // קודם בודקים זכאות, לפני תור
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
    public void selectStandingTicketsWithLotteryCode(UUID eventID,int amount,UUID areaID,UUID userID,boolean isConfirmedAge,String accessCode)
    {
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
            // קודם בודקים זכאות, לפני תור
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

    private void validateQueueAccess(UUID userId, UUID eventId)
    {
        QueueAccessResult result = queueManager.requestSelectionAccess(userId, eventId);
        if (!result.isAllowed())
            throw new IllegalStateException("User is waiting in queue. Position: " + result.getUserPositionInQueue() + "/" + result.getQueueSize());
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
            ActivePurchase activePurchase =
                    purchaseDomainService.selectSittingTickets(eventID, ticketIDs, userID, isConfirmedAge);

            queueManager.finishAccess(userID, eventID);
            queueManager.releaseBatch(eventID, 1);

            return toActivePurchaseDTO(activePurchase);
        } catch (DomainException e) {
            throw new IllegalStateException("couldn't select the sitting tickets");
        }
    }

    public ActivePurchaseDTO selectStandingTickets(UUID eventID, int amount, UUID areaID, UUID userID, boolean isConfirmedAge) {
        if (amount <= 0) {
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
            ActivePurchase activePurchase =
                    purchaseDomainService.selectStandingTickets(eventID, amount, userID, areaID, isConfirmedAge);

            queueManager.finishAccess(userID, eventID);
            queueManager.releaseBatch(eventID, 1);

            return toActivePurchaseDTO(activePurchase);
        } catch (DomainException e) {
            throw new IllegalStateException("couldn't select the standing tickets");
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
            ActivePurchase activePurchase = purchaseDomainService.viewActivePurchase(activePurchaseID);
            UUID userId = activePurchase.getUserID();
            boolean isSoldOut = purchaseDomainService.completePurchase(activePurchaseID, paymentDetails, couponCode);
            logger.info("Purchase completed successfully for activePurchaseID: " + activePurchaseID);
            notifier.notifyUser(activePurchase.getUserID(), "Purchase Complete");
            if(isSoldOut)
                {
                    String managerUsername = purchaseDomainService.getEventManager(activePurchase.getEventID());
                    notifier.notifyUser(managerUsername, "Tickets to event: " + activePurchase.getEventID() + " have been SOLD OUT");
                }
            eventPublisher.publish(new PurchaseCompletedEvent(userId));
        }
        catch (DomainException e) {
            logger.severe("Critical failure in completePurchase for ID " + activePurchaseID +
                    ". Reason: " + e.getMessage());
            throw new IllegalStateException("couldn't complete purchase");
        }
    }

    public List<PurchaseHistoryDTO> getAllHistory(UUID adminId) {
        validateAdmin(adminId);
        return toPurchaseHistoryDTOs(purchaseDomainService.getAllHistory());
    }

    public List<PurchaseHistoryDTO> getHistoryByUser(UUID adminId, UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        validateAdmin(adminId);

        return toPurchaseHistoryDTOs(purchaseDomainService.getHistoryByUser(userId));
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
        return toPurchaseHistoryDTOs(purchaseDomainService.getHistoryByEvent(eventId));
    }

    public List<PurchaseHistoryDTO> getHistoryByCompany(UUID adminId, UUID companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        validateAdmin(adminId);
        return toPurchaseHistoryDTOs(purchaseDomainService.getHistoryByCompany(companyId));
    }

    public List<PurchaseHistoryDTO> getPurchaseHistoryForMember(UUID memberId) {
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

            return toPurchaseHistoryDTOs(
                    purchaseDomainService.getPurchaseHistoryForMember(memberId));

        } catch (RuntimeException e) {
            logger.severe("action=getPurchaseHistoryForMember failed"
                    + ", caller=" + memberId
                    + ", target=PurchaseDomainService.getPurchaseHistoryForMember"
                    + ", params={memberId=" + memberId + "}"
                    + ", error=" + e.getMessage());
            throw e;
        }
    }

    public ActivePurchaseDTO viewActivePurchase(UUID activePurchaseId) {
        if (activePurchaseId == null) {
            throw new IllegalArgumentException("Active purchase ID is required");
        }

        try {
            ActivePurchase activePurchase = purchaseDomainService.viewActivePurchase(activePurchaseId);
            return toActivePurchaseDTO(activePurchase);
        } catch (DomainException e) {
            throw new IllegalStateException(e.getMessage());
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

    public List<PurchaseHistoryDTO> getEventPurchaseHistoryForOwner(String ownerName, UUID eventId) {
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

            return toPurchaseHistoryDTOs(result);

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
    private ActivePurchaseDTO toActivePurchaseDTO(ActivePurchase purchase) {
        if (purchase == null) {
            throw new IllegalStateException("Active purchase was not created");
        }

        ActivePurchaseDTO dto = new ActivePurchaseDTO();
        dto.activePurchaseId = purchase.getActivePurchaseId();
        dto.userId = purchase.getUserID();
        dto.eventId = purchase.getEventID();
        dto.ticketPrices = purchase.getTicketIDs();
        dto.endTime = purchase.getEndTime();
        dto.isGuestConfirmedAge = purchase.getGuestAgeConfirmed();
        dto.coupon = purchase.getCoupon();
        dto.price = purchase.getPrice();
        dto.maxWaitTime = purchase.getMaxWaitTime();
        dto.lastUpdate = purchase.getLastUpdate();

        dto.ticketsAmount = (dto.ticketPrices == null) ? 0 : dto.ticketPrices.size();
        populateEventFields(dto, purchase.getEventID());

        return dto;
    }
    private PurchaseHistoryDTO toPurchaseHistoryDTO(PurchaseHistory history) {
        PurchaseHistoryDTO dto = new PurchaseHistoryDTO();

        dto.userId = history.getUserId();
        dto.eventId = history.getEventId();
        dto.ticketIds = history.getTicketIds();
        dto.purchaseDate = history.getPurchaseDate();
        dto.ticketsAmount = (dto.ticketIds == null) ? 0 : dto.ticketIds.size();

        if (history.getPayment() != null) {
            dto.paymentInfo = history.getPayment().toString();
            dto.totalPrice = history.getPayment().getTotal();
        }

        populateEventFields(dto, history.getEventId());

        return dto;
    }

    private void populateEventFields(PurchaseHistoryDTO dto, UUID eventId) {
        Event event = purchaseDomainService.findEventById(eventId);
        if (event == null) {
            return;
        }
        dto.eventName = event.getName();
        dto.eventDate = event.getDate();
        dto.eventLocation = event.getLocation();
    }

    private void populateEventFields(ActivePurchaseDTO dto, UUID eventId) {
        Event event = purchaseDomainService.findEventById(eventId);
        if (event == null) {
            return;
        }
        dto.eventName = event.getName();
        dto.eventDate = event.getDate();
        dto.eventLocation = event.getLocation();
    }
    private List<PurchaseHistoryDTO> toPurchaseHistoryDTOs(List<PurchaseHistory> histories) {
        List<PurchaseHistoryDTO> result = new ArrayList<>();

        for (PurchaseHistory history : histories) {
            result.add(toPurchaseHistoryDTO(history));
        }

        return result;
    }
}