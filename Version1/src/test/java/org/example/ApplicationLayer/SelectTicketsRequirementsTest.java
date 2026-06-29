package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.PurchaseDTOs.ActivePurchaseDTO;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.Events.TicketReservedEvent;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PurchaseDomainService;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Requirement-focused coverage for the "select tickets" flow at the
 * application-service boundary. These tests pin the orchestration contract that
 * the manual grading session depends on:
 *
 *   1. Queue access is required BEFORE any reservation happens.
 *   2. A SUCCESSFUL selection releases the selection slot and admits the next
 *      queued user (finishAccess + releaseBatch) and publishes a reservation event.
 *   3. A FAILED selection keeps the user's selection slot so they can retry a
 *      different seat within their window (regression guard for the bug where a
 *      failed checkout left the user stuck on "Selection access expired").
 *   4. Input validation and lottery gating reject bad calls before touching the
 *      domain or the queue.
 */
public class SelectTicketsRequirementsTest {

    private PurchaseDomainService domain;
    private EventPublisher eventPublisher;
    private QueueManager queueManager;
    private INotifier notifier;
    private PurchaseService service;

    private UUID eventId;
    private UUID userId;
    private UUID areaId;
    private UUID seatA;
    private UUID seatB;

    @Before
    public void setUp() {
        domain = mock(PurchaseDomainService.class);
        eventPublisher = mock(EventPublisher.class);
        queueManager = mock(QueueManager.class);
        notifier = mock(INotifier.class);
        service = new PurchaseService(domain, eventPublisher, queueManager, notifier);

        eventId = UUID.randomUUID();
        userId = UUID.randomUUID();
        areaId = UUID.randomUUID();
        seatA = UUID.randomUUID();
        seatB = UUID.randomUUID();
    }

    /** DTO mapping reads the event back; null is fine for these assertions. */
    private void stubDtoEventLookup() {
        when(domain.findEventById(eventId)).thenReturn(null);
    }

    // ---------- success contract ----------

    @Test
    public void selectTickets_onSuccess_requiresAccessThenReleasesSlotAndPublishesEvent() {
        stubDtoEventLookup();
        ActivePurchase reserved = activePurchase(Map.of(seatA, 100f));
        when(domain.selectTickets(eq(eventId), eq(List.of(seatA)), eq(0), any(), eq(userId), anyBoolean(), any()))
                .thenReturn(reserved);

        ActivePurchaseDTO dto =
                service.selectTickets(eventId, List.of(seatA), 0, null, userId, true, null);

        assertNotNull(dto);
        verify(queueManager).requireSelectionAccess(userId, eventId);
        verify(domain).selectTickets(eventId, List.of(seatA), 0, null, userId, true, null);
        verify(queueManager).finishAccess(userId, eventId);
        verify(queueManager).releaseBatch(eventId, 1);
        verify(eventPublisher).publish(any(TicketReservedEvent.class));
    }

    // ---------- the regression bug: failure must keep selection access ----------

    @Test
    public void selectTickets_whenSeatTaken_keepsSelectionAccessAndDoesNotAdmitNextUser() {
        when(domain.selectTickets(eq(eventId), eq(List.of(seatA)), eq(0), any(), eq(userId), anyBoolean(), any()))
                .thenThrow(new DomainException("The selected seat is no longer available"));

        assertThrows(IllegalStateException.class,
                () -> service.selectTickets(eventId, List.of(seatA), 0, null, userId, true, null));

        // Access was checked, but the slot must NOT be released on failure.
        verify(queueManager).requireSelectionAccess(userId, eventId);
        verify(queueManager, never()).finishAccess(userId, eventId);
        verify(queueManager, never()).releaseBatch(eq(eventId), anyInt());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    public void selectTickets_afterFailedSeat_canImmediatelyRetryDifferentSeat() {
        stubDtoEventLookup();
        // First attempt: seat A is taken -> fails.
        when(domain.selectTickets(eq(eventId), eq(List.of(seatA)), eq(0), any(), eq(userId), anyBoolean(), any()))
                .thenThrow(new DomainException("The selected seat is no longer available"));
        // Retry: seat B is free -> succeeds.
        ActivePurchase reserved = activePurchase(Map.of(seatB, 100f));
        when(domain.selectTickets(eq(eventId), eq(List.of(seatB)), eq(0), any(), eq(userId), anyBoolean(), any()))
                .thenReturn(reserved);

        assertThrows(IllegalStateException.class,
                () -> service.selectTickets(eventId, List.of(seatA), 0, null, userId, true, null));

        // The retry must go through (requireSelectionAccess still passes because
        // we never revoked it) and finally release the slot exactly once.
        ActivePurchaseDTO dto =
                service.selectTickets(eventId, List.of(seatB), 0, null, userId, true, null);

        assertNotNull(dto);
        verify(queueManager, times(2)).requireSelectionAccess(userId, eventId);
        verify(queueManager, times(1)).finishAccess(userId, eventId);
        verify(queueManager, times(1)).releaseBatch(eventId, 1);
    }

    @Test
    public void selectTickets_whenNoSelectionAccess_failsBeforeTouchingDomain() {
        doThrow(new IllegalStateException(
                "Selection access expired or was not granted. Please join the queue again."))
                .when(queueManager).requireSelectionAccess(userId, eventId);

        assertThrows(IllegalStateException.class,
                () -> service.selectTickets(eventId, List.of(seatA), 0, null, userId, true, null));

        verify(domain, never()).selectTickets(any(), any(), anyInt(), any(), any(), anyBoolean(), any());
        verify(queueManager, never()).finishAccess(any(), any());
    }

    // ---------- input validation (edge cases) ----------

    @Test
    public void selectTickets_rejectsInvalidInputBeforeQueueAndDomain() {
        assertThrows(IllegalArgumentException.class,
                () -> service.selectTickets(null, List.of(seatA), 0, null, userId, true, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectTickets(eventId, List.of(seatA), 0, null, null, true, null));
        // Nothing selected: no sitting tickets and no standing amount.
        assertThrows(IllegalArgumentException.class,
                () -> service.selectTickets(eventId, List.of(), 0, null, userId, true, null));
        // Standing requested but no area provided.
        assertThrows(IllegalArgumentException.class,
                () -> service.selectTickets(eventId, List.of(), 2, null, userId, true, null));

        verifyNoInteractions(domain);
        verify(queueManager, never()).requireSelectionAccess(any(), any());
    }

    // ---------- lottery gating ----------

    @Test
    public void selectTickets_lotteryEvent_requiresAccessCode() {
        when(domain.isLotteryEvent(eventId)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.selectTickets(eventId, List.of(seatA), 0, null, userId, true, "   "));

        verify(domain, never()).selectTickets(any(), any(), anyInt(), any(), any(), anyBoolean(), any());
        verify(queueManager, never()).finishAccess(any(), any());
    }

    @Test
    public void selectTickets_lotteryEvent_validatesEligibilityBeforeReserving() {
        when(domain.isLotteryEvent(eventId)).thenReturn(true);
        doThrow(new DomainException("Invalid or expired lottery access code"))
                .when(domain).validateSelectionEligibility(eventId, userId, "CODE");

        assertThrows(RuntimeException.class,
                () -> service.selectTickets(eventId, List.of(seatA), 0, null, userId, true, "CODE"));

        verify(domain).validateSelectionEligibility(eventId, userId, "CODE");
        verify(domain, never()).selectTickets(any(), any(), anyInt(), any(), any(), anyBoolean(), any());
        verify(queueManager, never()).finishAccess(any(), any());
    }

    // ---------- convenience sitting/standing methods share the same contract ----------

    @Test
    public void selectSittingTickets_success_releasesSlot_andFailureKeepsIt() {
        stubDtoEventLookup();
        ActivePurchase reserved = activePurchase(Map.of(seatA, 100f));
        when(domain.selectSittingTickets(eventId, List.of(seatA), userId, true)).thenReturn(reserved);

        assertNotNull(service.selectSittingTickets(eventId, List.of(seatA), userId, true));
        verify(queueManager).finishAccess(userId, eventId);
        verify(queueManager).releaseBatch(eventId, 1);

        // Now a contended seat fails and must NOT release a second time.
        doThrow(new DomainException("The selected seat is no longer available"))
                .when(domain).selectSittingTickets(eventId, List.of(seatB), userId, true);

        assertThrows(IllegalStateException.class,
                () -> service.selectSittingTickets(eventId, List.of(seatB), userId, true));

        verify(queueManager, times(1)).finishAccess(userId, eventId);
        verify(queueManager, times(1)).releaseBatch(eventId, 1);
    }

    @Test
    public void selectStandingTickets_whenSoldOut_keepsSelectionAccess() {
        doThrow(new DomainException("Not enough standing capacity"))
                .when(domain).selectStandingTickets(eventId, 3, userId, areaId, true);

        assertThrows(IllegalStateException.class,
                () -> service.selectStandingTickets(eventId, 3, areaId, userId, true));

        verify(queueManager).requireSelectionAccess(userId, eventId);
        verify(queueManager, never()).finishAccess(userId, eventId);
        verify(queueManager, never()).releaseBatch(eq(eventId), anyInt());
    }

    @Test
    public void selectStandingTickets_rejectsNonPositiveAmountAndNullUser() {
        assertThrows(IllegalArgumentException.class,
                () -> service.selectStandingTickets(eventId, 0, areaId, userId, true));
        assertThrows(IllegalArgumentException.class,
                () -> service.selectStandingTickets(eventId, 1, areaId, null, true));

        verify(queueManager, never()).requireSelectionAccess(any(), any());
    }

    // ---------- lottery-code methods release only on success ----------

    @Test
    public void selectSittingTicketsWithLotteryCode_success_releasesSlotAfterReserving() {
        stubDtoEventLookup();
        ActivePurchase reserved = activePurchase(Map.of(seatA, 100f));
        when(domain.selectSittingTicketsWithLotteryCode(eventId, List.of(seatA), userId, true, "CODE"))
                .thenReturn(reserved);

        assertNotNull(service.selectSittingTicketsWithLotteryCode(
                eventId, List.of(seatA), userId, true, "CODE"));

        verify(domain).validateSelectionEligibility(eventId, userId, "CODE");
        verify(queueManager).requireSelectionAccess(userId, eventId);
        verify(queueManager).finishAccess(userId, eventId);
        verify(queueManager).releaseBatch(eventId, 1);
    }

    @Test
    public void selectSittingTicketsWithLotteryCode_whenReservationFails_keepsSelectionAccess() {
        doThrow(new DomainException("The selected seat is no longer available"))
                .when(domain).selectSittingTicketsWithLotteryCode(eventId, List.of(seatA), userId, true, "CODE");

        assertThrows(IllegalStateException.class,
                () -> service.selectSittingTicketsWithLotteryCode(eventId, List.of(seatA), userId, true, "CODE"));

        verify(queueManager).requireSelectionAccess(userId, eventId);
        verify(queueManager, never()).finishAccess(userId, eventId);
        verify(queueManager, never()).releaseBatch(eq(eventId), anyInt());
    }

    private ActivePurchase activePurchase(Map<UUID, Float> ticketPrices) {
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
