package org.example.AdditionalTests;

import org.example.ApplicationLayer.*;
import org.example.ApplicationLayer.dto.PurchaseDTOs.ActivePurchaseDTO;
import org.example.ApplicationLayer.dto.PurchaseDTOs.LotteryStatusDTO;
import org.example.ApplicationLayer.dto.PurchaseDTOs.SelectionAccessDTO;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.Events.LotteryWonEvent;
import org.example.DomainLayer.Events.PurchaseCompletedEvent;
import org.example.DomainLayer.Events.TicketReservedEvent;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class PurchaseServiceAdditionalTests {

    private PurchaseDomainService domain;
    private EventPublisher eventPublisher;
    private QueueManager queueManager;
    private INotifier notifier;
    private PurchaseService service;

    @Before
    public void setUp() {
        domain = mock(PurchaseDomainService.class);
        eventPublisher = mock(EventPublisher.class);
        queueManager = mock(QueueManager.class);
        notifier = mock(INotifier.class);
        service = new PurchaseService(domain, eventPublisher, queueManager, notifier);
    }

    @Test
    public void validateAdmin_rejectsNullAndNonAdminAndAllowsAdmin() {
        UUID adminId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> service.validateAdmin(null));

        when(domain.validateAdmin(adminId)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.validateAdmin(adminId));

        when(domain.validateAdmin(adminId)).thenReturn(true);
        service.validateAdmin(adminId);
    }

    @Test
    public void selectionAccessEndpoints_convertAllowedWaitingAndMissingQueueResultsToDtos() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);

        when(queueManager.requestSelectionAccess(userId, eventId))
                .thenReturn(QueueAccessResult.allowed(expiresAt));
        SelectionAccessDTO allowed = service.requestSelectionAccess(userId, eventId);
        assertNotNull(allowed);

        when(queueManager.getSelectionAccessStatus(userId, eventId))
                .thenReturn(QueueAccessResult.waiting(3, 10));
        SelectionAccessDTO waiting = service.getSelectionAccessStatus(userId, eventId);
        assertNotNull(waiting);

        when(queueManager.getSelectionAccessStatus(userId, eventId))
                .thenReturn(QueueAccessResult.waiting(-1, 0));
        SelectionAccessDTO notQueued = service.getSelectionAccessStatus(userId, eventId);
        assertNotNull(notQueued);
    }

    @Test
    public void getLotteryStatus_skipsWinnerQueriesWhenEventIsNotLotteryAndQueriesAllWhenItIs() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(domain.isLotteryEvent(eventId)).thenReturn(false);
        LotteryStatusDTO notLottery = service.getLotteryStatus(eventId, userId);
        assertNotNull(notLottery);
        verify(domain, never()).areLotteryWinnersDrawn(eventId);
        verify(domain, never()).isUserWinner(eventId, userId);
        verify(domain, never()).isUserRegisteredToLottery(eventId, userId);

        when(domain.isLotteryEvent(eventId)).thenReturn(true);
        when(domain.areLotteryWinnersDrawn(eventId)).thenReturn(true);
        when(domain.isUserWinner(eventId, userId)).thenReturn(true);
        when(domain.isUserRegisteredToLottery(eventId, userId)).thenReturn(true);

        LotteryStatusDTO lottery = service.getLotteryStatus(eventId, userId);

        assertNotNull(lottery);
        verify(domain).areLotteryWinnersDrawn(eventId);
        verify(domain).isUserWinner(eventId, userId);
        verify(domain).isUserRegisteredToLottery(eventId, userId);
    }

    @Test
    public void sendProducerMessageToEventBuyers_validatesAndNotifiesDistinctBuyersOnly() {
        UUID eventId = UUID.randomUUID();
        UUID buyerOne = UUID.randomUUID();
        UUID buyerTwo = UUID.randomUUID();
        PurchaseHistory first = mock(PurchaseHistory.class);
        PurchaseHistory duplicate = mock(PurchaseHistory.class);
        PurchaseHistory second = mock(PurchaseHistory.class);

        when(first.getUserId()).thenReturn(buyerOne);
        when(duplicate.getUserId()).thenReturn(buyerOne);
        when(second.getUserId()).thenReturn(buyerTwo);
        when(domain.getHistoryByEvent(eventId)).thenReturn(List.of(first, duplicate, second));

        assertThrows(IllegalArgumentException.class, () -> service.sendProducerMessageToEventBuyers(null, "hello"));
        assertThrows(IllegalArgumentException.class, () -> service.sendProducerMessageToEventBuyers(eventId, null));
        assertThrows(IllegalArgumentException.class, () -> service.sendProducerMessageToEventBuyers(eventId, "   "));

        service.sendProducerMessageToEventBuyers(eventId, "event update");

        verify(notifier).notifyUser(buyerOne, "event update");
        verify(notifier).notifyUser(buyerTwo, "event update");
        verify(notifier, times(2)).notifyUser(any(UUID.class), eq("event update"));
    }

    @Test
    public void registerToLottery_validatesDelegatesAndWrapsDomainException() {
        UUID eventId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> service.registerToLottery(null, memberId, 1));
        assertThrows(IllegalArgumentException.class, () -> service.registerToLottery(eventId, null, 1));
        assertThrows(IllegalArgumentException.class, () -> service.registerToLottery(eventId, memberId, 0));

        service.registerToLottery(eventId, memberId, 2);
        verify(domain).registerToLottery(eventId, memberId, 2);

        doThrow(new DomainException("closed"))
                .when(domain).registerToLottery(eventId, memberId, 3);

        assertThrows(IllegalStateException.class, () -> service.registerToLottery(eventId, memberId, 3));
    }

    @Test
    public void drawLotteryForEvent_notifiesWinnersLosersAndFallbackWinners() {
        UUID eventId = UUID.randomUUID();
        LocalDateTime expiry = LocalDateTime.now().plusHours(24);
        String registeredWinner = "winner-1";
        String registeredLoser = "loser-1";
        String fallbackWinner = UUID.randomUUID().toString();
        Event event = mock(Event.class);

        when(domain.getRegisteredUsersForLottery(eventId))
                .thenReturn(Set.of(registeredWinner, registeredLoser));
        when(domain.drawLotteryForEvent(eventId, expiry))
                .thenReturn(Map.of(
                        registeredWinner, "CODE-1",
                        fallbackWinner, "CODE-2"
                ));
        when(domain.findEventById(eventId)).thenReturn(event);
        when(event.getName()).thenReturn("Rock Show");

        Map<String, String> result = service.drawLotteryForEvent(eventId, expiry);

        assertEquals("CODE-1", result.get(registeredWinner));
        assertEquals("CODE-2", result.get(fallbackWinner));

        verify(eventPublisher, times(2)).publish(any(LotteryWonEvent.class));
        verify(notifier).notifyUser(eq(registeredWinner), contains("CODE-1"));
        verify(notifier).notifyUser(eq(registeredLoser), contains("did not win"));
        verify(notifier).notifyUser(eq(UUID.fromString(fallbackWinner)), contains("CODE-2"));
    }

    @Test
    public void drawLotteryForEvent_usesDefaultEventNameAndStringFallbackWhenNeeded() {
        UUID eventId = UUID.randomUUID();
        LocalDateTime expiry = LocalDateTime.now().plusHours(24);
        String nonUuidWinner = "legacy-winner";

        when(domain.getRegisteredUsersForLottery(eventId)).thenReturn(Set.of());
        when(domain.drawLotteryForEvent(eventId, expiry)).thenReturn(Map.of(nonUuidWinner, "CODE"));
        when(domain.findEventById(eventId)).thenThrow(new RuntimeException("view failed"));

        Map<String, String> result = service.drawLotteryForEvent(eventId, expiry);

        assertEquals("CODE", result.get(nonUuidWinner));
        verify(notifier).notifyUser(eq(nonUuidWinner), contains("your event"));
    }

    @Test
    public void drawLotteryForEvent_validatesAndWrapsDomainException() {
        UUID eventId = UUID.randomUUID();
        LocalDateTime expiry = LocalDateTime.now().plusHours(24);

        assertThrows(IllegalArgumentException.class, () -> service.drawLotteryForEvent(null, expiry));
        assertThrows(IllegalArgumentException.class, () -> service.drawLotteryForEvent(eventId, null));

        when(domain.getRegisteredUsersForLottery(eventId)).thenThrow(new DomainException("not lottery"));

        assertThrows(IllegalStateException.class, () -> service.drawLotteryForEvent(eventId, expiry));
    }

    @Test
    public void selectTickets_validationLotteryDomainAndSuccessBranches() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> service.selectSittingTickets(eventId, null, userId, true));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectSittingTickets(eventId, List.of(), userId, true));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectSittingTickets(eventId, List.of(ticketId), null, true));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectStandingTickets(eventId, 0, areaId, userId, true));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectStandingTickets(eventId, 1, areaId, null, true));

        when(domain.isLotteryEvent(eventId)).thenReturn(true);
        assertThrows(IllegalStateException.class,
                () -> service.selectSittingTickets(eventId, List.of(ticketId), userId, true));
        assertThrows(IllegalStateException.class,
                () -> service.selectStandingTickets(eventId, 1, areaId, userId, true));

        when(domain.isLotteryEvent(eventId)).thenReturn(false);
        ActivePurchase sittingPurchase = activePurchase(userId, eventId, Map.of(ticketId, 100f));
        when(domain.selectSittingTickets(eventId, List.of(ticketId), userId, true)).thenReturn(sittingPurchase);
        when(domain.findEventById(eventId)).thenReturn(null);

        ActivePurchaseDTO sittingDto = service.selectSittingTickets(eventId, List.of(ticketId), userId, true);
        assertNotNull(sittingDto);
        verify(queueManager).requireSelectionAccess(userId, eventId);
        verify(queueManager).finishAccess(userId, eventId);
        verify(queueManager).releaseBatch(eventId, 1);
        verify(eventPublisher).publish(any(TicketReservedEvent.class));

        doThrow(new DomainException("sold"))
                .when(domain).selectStandingTickets(eventId, 1, userId, areaId, true);
        assertThrows(IllegalStateException.class,
                () -> service.selectStandingTickets(eventId, 1, areaId, userId, true));
    }

    @Test
    public void lotteryCodeSelection_validatesDomainAndKeepsQueueAccessOnFailure() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> service.selectSittingTicketsWithLotteryCode(null, List.of(ticketId), userId, true, "CODE"));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectSittingTicketsWithLotteryCode(eventId, null, userId, true, "CODE"));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectSittingTicketsWithLotteryCode(eventId, List.of(ticketId), null, true, "CODE"));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectSittingTicketsWithLotteryCode(eventId, List.of(ticketId), userId, true, " "));

        assertThrows(IllegalArgumentException.class,
                () -> service.selectStandingTicketsWithLotteryCode(null, 1, areaId, userId, true, "CODE"));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectStandingTicketsWithLotteryCode(eventId, 0, areaId, userId, true, "CODE"));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectStandingTicketsWithLotteryCode(eventId, 1, null, userId, true, "CODE"));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectStandingTicketsWithLotteryCode(eventId, 1, areaId, null, true, "CODE"));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectStandingTicketsWithLotteryCode(eventId, 1, areaId, userId, true, null));

        doThrow(new DomainException("not winner"))
                .when(domain).validateSelectionEligibility(eventId, userId, "CODE");

        assertThrows(IllegalStateException.class,
                () -> service.selectSittingTicketsWithLotteryCode(eventId, List.of(ticketId), userId, true, "CODE"));

        // A failed selection must NOT release the user's selection slot, otherwise
        // a retry (e.g. with a corrected code/seat) wrongly fails with
        // "Selection access expired or was not granted".
        verify(queueManager, never()).finishAccess(userId, eventId);
        verify(queueManager, never()).releaseBatch(eventId, 1);
    }

    @Test
    public void completePurchase_validationSuccessSoldOutHistoryLookupAndDomainFailureBranches() {
        UUID activePurchaseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        PaymentDetails paymentDetails = mock(PaymentDetails.class);
        ActivePurchase activePurchase = activePurchase(userId, eventId, Map.of(UUID.randomUUID(), 100f));
        PurchaseHistory oldHistory = mock(PurchaseHistory.class);
        PurchaseHistory newHistory = mock(PurchaseHistory.class);

        assertThrows(IllegalArgumentException.class, () -> service.completePurchase(null, paymentDetails, null));
        assertThrows(IllegalArgumentException.class, () -> service.completePurchase(activePurchaseId, null, null));

        when(domain.viewActivePurchase(activePurchaseId)).thenReturn(activePurchase);
        when(domain.completePurchase(activePurchaseId, paymentDetails, "SAVE10")).thenReturn(true);
        when(domain.getEventManager(eventId)).thenReturn("manager@example.com");
        when(oldHistory.getUserId()).thenReturn(userId);
        when(oldHistory.getPurchaseDate()).thenReturn(LocalDateTime.now().minusMinutes(5));
        when(oldHistory.getIssuedTicketReference()).thenReturn("OLD");
        when(newHistory.getUserId()).thenReturn(userId);
        when(newHistory.getPurchaseDate()).thenReturn(LocalDateTime.now());
        when(newHistory.getIssuedTicketReference()).thenReturn(" A , , B ");
        when(domain.getHistoryByEvent(eventId)).thenReturn(List.of(oldHistory, newHistory));

        List<String> refs = service.completePurchase(activePurchaseId, paymentDetails, " SAVE10 ");

        assertEquals(List.of("A", "B"), refs);
        verify(notifier).notifyUser(userId, "Purchase Complete");
        verify(notifier).notifyUser(eq("manager@example.com"), contains("SOLD OUT"));
        verify(eventPublisher).publish(any(PurchaseCompletedEvent.class));

        UUID failingPurchaseId = UUID.randomUUID();
        when(domain.viewActivePurchase(failingPurchaseId)).thenThrow(new DomainException("missing"));
        assertThrows(IllegalStateException.class,
                () -> service.completePurchase(failingPurchaseId, paymentDetails, null));
    }

    @Test
    public void activePurchaseViewAndMutationMethods_validateSuccessAndDomainFailures() {
        UUID activePurchaseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        ActivePurchase activePurchase = activePurchase(userId, eventId, Map.of(ticketId, 50f));

        assertThrows(IllegalArgumentException.class, () -> service.viewActivePurchase(null));
        assertThrows(IllegalArgumentException.class, () -> service.viewActivePurchaseForEvent(null, eventId));
        assertThrows(IllegalArgumentException.class, () -> service.viewActivePurchaseForEvent(userId, null));
        assertThrows(IllegalArgumentException.class, () -> service.viewActivePurchasesForUser(null));
        assertThrows(IllegalArgumentException.class, () -> service.cancelActivePurchase(null));
        assertThrows(IllegalArgumentException.class, () -> service.updateActivePurchaseSittingTickets(null, List.of(ticketId)));
        assertThrows(IllegalArgumentException.class, () -> service.updateActivePurchaseSittingTickets(activePurchaseId, List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.updateActivePurchaseStandingTickets(null, 1, areaId));
        assertThrows(IllegalArgumentException.class, () -> service.updateActivePurchaseStandingTickets(activePurchaseId, 0, areaId));

        when(domain.viewActivePurchase(activePurchaseId)).thenReturn(activePurchase);
        when(domain.findEventById(eventId)).thenReturn(null);
        assertNotNull(service.viewActivePurchase(activePurchaseId));

        when(domain.findActivePurchaseByUserAndEvent(userId, eventId)).thenReturn(null);
        assertNull(service.viewActivePurchaseForEvent(userId, eventId));

        when(domain.findActivePurchasesByUser(userId)).thenReturn(List.of(activePurchase));
        assertEquals(1, service.viewActivePurchasesForUser(userId).size());

        service.cancelActivePurchase(activePurchaseId);
        service.updateActivePurchaseSittingTickets(activePurchaseId, List.of(ticketId));
        service.updateActivePurchaseStandingTickets(activePurchaseId, 1, areaId);

        UUID failingId = UUID.randomUUID();
        doThrow(new DomainException("domain"))
                .when(domain).cancelActivePurchase(failingId);
        assertThrows(IllegalStateException.class, () -> service.cancelActivePurchase(failingId));
    }

    @Test
    public void purchaseHistoryQueries_validateAuthorizationAndMapDomainResults() {
        UUID memberId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String ownerName = "owner@example.com";
        PurchaseHistory history = mock(PurchaseHistory.class);

        assertThrows(IllegalArgumentException.class, () -> service.getPurchaseHistoryForMember(null));
        when(domain.memberExists(memberId)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.getPurchaseHistoryForMember(memberId));

        when(domain.memberExists(memberId)).thenReturn(true);
        when(domain.isMember(memberId)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.getPurchaseHistoryForMember(memberId));

        when(domain.isMember(memberId)).thenReturn(true);
        when(domain.isMemberLoggedIn(memberId)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.getPurchaseHistoryForMember(memberId));

        when(domain.isMemberLoggedIn(memberId)).thenReturn(true);
        when(domain.getPurchaseHistoryForMember(memberId)).thenReturn(List.of(history));
        when(history.getUserId()).thenReturn(memberId);
        when(history.getEventId()).thenReturn(eventId);
        when(history.getTicketIds()).thenReturn(List.of(UUID.randomUUID()));
        when(history.getPurchaseDate()).thenReturn(LocalDateTime.now());
        when(domain.findEventById(eventId)).thenReturn(null);
        assertEquals(1, service.getPurchaseHistoryForMember(memberId).size());

        assertThrows(IllegalArgumentException.class, () -> service.getEventPurchaseHistoryForOwner(null, eventId));
        assertThrows(IllegalArgumentException.class, () -> service.getEventPurchaseHistoryForOwner("   ", eventId));
        assertThrows(IllegalArgumentException.class, () -> service.getEventPurchaseHistoryForOwner(ownerName, null));

        when(domain.eventExists(eventId)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.getEventPurchaseHistoryForOwner(ownerName, eventId));

        when(domain.eventExists(eventId)).thenReturn(true);
        when(domain.isCompanyOwnerOfEvent(ownerName, eventId)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.getEventPurchaseHistoryForOwner(ownerName, eventId));

        when(domain.isCompanyOwnerOfEvent(ownerName, eventId)).thenReturn(true);
        when(domain.getHistoryByEvent(eventId)).thenReturn(List.of(history));
        assertEquals(1, service.getEventPurchaseHistoryForOwner(ownerName, eventId).size());
    }

    private ActivePurchase activePurchase(UUID userId, UUID eventId, Map<UUID, Float> ticketPrices) {
        ActivePurchase activePurchase = mock(ActivePurchase.class);
        when(activePurchase.getActivePurchaseId()).thenReturn(UUID.randomUUID());
        when(activePurchase.getUserID()).thenReturn(userId);
        when(activePurchase.getEventID()).thenReturn(eventId);
        when(activePurchase.getTicketIDs()).thenReturn(new LinkedHashMap<>(ticketPrices));
        when(activePurchase.getEndTime()).thenReturn(LocalDateTime.now().plusMinutes(10));
        when(activePurchase.getGuestAgeConfirmed()).thenReturn(true);
        when(activePurchase.getCoupon()).thenReturn(null);
        when(activePurchase.getLastUpdate()).thenReturn(LocalDateTime.now());
        return activePurchase;
    }
}
