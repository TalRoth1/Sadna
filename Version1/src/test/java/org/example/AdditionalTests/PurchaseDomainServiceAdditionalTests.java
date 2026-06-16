package org.example.AdditionalTests;

import org.example.ApplicationLayer.IPaymentGateway;
import org.example.ApplicationLayer.ITicketingGateway;
import org.example.ApplicationLayer.PaymentDetails;
import org.example.ApplicationLayer.PaymentResult;
import org.example.ApplicationLayer.dto.SalesReport;
import org.example.DomainLayer.*;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.SittingTicket;
import org.example.DomainLayer.EventAggregate.Ticket;
import org.example.DomainLayer.EventAggregate.TicketStatus;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.PolicyManagment.DiscountPolicy;
import org.example.DomainLayer.PolicyManagment.DiscountType;
import org.example.DomainLayer.PolicyManagment.PurchasePolicy;
import org.example.DomainLayer.PurchaseHistoryAggregate.Payment;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.User;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PurchaseDomainServiceAdditionalTests {

    private IHistoryRepository historyRepository;
    private IEventRepository eventRepository;
    private IPurchaseRepository purchaseRepository;
    private ICompanyRepository companyRepository;
    private IUserRepository userRepository;
    private ILotteryRepository lotteryRepository;
    private PurchaseDomainService service;

    @Before
    public void setUp() {
        historyRepository = mock(IHistoryRepository.class);
        eventRepository = mock(IEventRepository.class);
        purchaseRepository = mock(IPurchaseRepository.class);
        companyRepository = mock(ICompanyRepository.class);
        userRepository = mock(IUserRepository.class);
        lotteryRepository = mock(ILotteryRepository.class);

        service = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );
    }

    private Event event(UUID eventId, UUID companyId) {
        return new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(30),
                "Tel Aviv",
                "Artist",
                "Concert",
                EventStatus.ACTIVE,
                DiscountType.ALL
        );
    }

    private Event eventWithOneSittingTicket(UUID eventId, UUID companyId, UUID areaId, UUID ticketId) {
        Event event = event(eventId, companyId);
        event.getLayout().addArea(new SittingArea(areaId, 100f));
        event.addTicket(new SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        return event;
    }

    private ActivePurchase activePurchase(UUID userId, UUID eventId, LocalDateTime expiresAt) {
        Map<UUID, Float> tickets = new LinkedHashMap<>();
        tickets.put(UUID.randomUUID(), 100f);
        return new ActivePurchase(userId, eventId, tickets, expiresAt);
    }

    @Test
    public void addPurchaseToHistory_rejectsMissingDataAndStoresIssuedTicketReference() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Payment payment = new Payment(120f, "paid", 777);

        assertThrows(IllegalArgumentException.class,
                () -> service.addPurchaseToHistory(userId, null, eventId, payment));

        assertThrows(IllegalArgumentException.class,
                () -> service.addPurchaseToHistory(userId, List.of(ticketId), eventId, null));

        service.addPurchaseToHistory(userId, List.of(ticketId), eventId, payment, "REF-1,REF-2");

        org.mockito.ArgumentCaptor<PurchaseHistory> captor =
                org.mockito.ArgumentCaptor.forClass(PurchaseHistory.class);
        verify(historyRepository).add(captor.capture());

        assertEquals(userId, captor.getValue().getUserId());
        assertEquals(eventId, captor.getValue().getEventId());
        assertEquals(List.of(ticketId), captor.getValue().getTicketIds());
        assertEquals("REF-1,REF-2", captor.getValue().getIssuedTicketReference());
    }

    @Test
    public void findEventAndActivePurchaseReadModels_coverNullExpiredAndValidBranches() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();
        Event event = event(eventId, UUID.randomUUID());

        assertNull(service.findEventById(null));

        when(eventRepository.getById(eventId)).thenReturn(event);
        assertSame(event, service.findEventById(eventId));

        assertThrows(DomainException.class,
                () -> service.findActivePurchaseByUserAndEvent(null, eventId));
        assertThrows(DomainException.class,
                () -> service.findActivePurchaseByUserAndEvent(userId, null));

        when(purchaseRepository.findByUserAndEvent(userId, eventId)).thenReturn(null);
        assertNull(service.findActivePurchaseByUserAndEvent(userId, eventId));

        ActivePurchase expired = activePurchase(userId, eventId, LocalDateTime.now().minusMinutes(1));
        when(purchaseRepository.findByUserAndEvent(userId, eventId)).thenReturn(expired);
        assertNull(service.findActivePurchaseByUserAndEvent(userId, eventId));

        ActivePurchase valid = activePurchase(userId, otherEventId, LocalDateTime.now().plusMinutes(10));
        when(purchaseRepository.findByUserAndEvent(userId, otherEventId)).thenReturn(valid);
        assertSame(valid, service.findActivePurchaseByUserAndEvent(userId, otherEventId));
    }

    @Test
    public void findActivePurchasesByUser_filtersOtherUsersExpiredAndInactivePurchases() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        ActivePurchase good = mock(ActivePurchase.class);
        when(good.getUserID()).thenReturn(userId);
        when(good.isExpired(any(LocalDateTime.class))).thenReturn(false);
        when(good.getLastUpdate()).thenReturn(LocalDateTime.now());
        when(good.getMaxWaitTime()).thenReturn(10f);

        ActivePurchase otherUser = mock(ActivePurchase.class);
        when(otherUser.getUserID()).thenReturn(otherUserId);

        ActivePurchase expired = mock(ActivePurchase.class);
        when(expired.getUserID()).thenReturn(userId);
        when(expired.isExpired(any(LocalDateTime.class))).thenReturn(true);

        ActivePurchase stale = mock(ActivePurchase.class);
        when(stale.getUserID()).thenReturn(userId);
        when(stale.isExpired(any(LocalDateTime.class))).thenReturn(false);
        when(stale.getLastUpdate()).thenReturn(LocalDateTime.now().minusHours(1));
        when(stale.getMaxWaitTime()).thenReturn(1f);

        when(purchaseRepository.findAll()).thenReturn(List.of(good, otherUser, expired, stale));

        assertEquals(List.of(good), service.findActivePurchasesByUser(userId));
        assertThrows(DomainException.class, () -> service.findActivePurchasesByUser(null));
    }

    @Test
    public void validateSelectionEligibility_regularAndLotteryBranches() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Event event = event(eventId, companyId);

        assertThrows(DomainException.class,
                () -> service.validateSelectionEligibility(null, userId, null));
        assertThrows(DomainException.class,
                () -> service.validateSelectionEligibility(eventId, null, null));

        when(userRepository.exists(userId)).thenReturn(false);
        assertThrows(DomainException.class,
                () -> service.validateSelectionEligibility(eventId, userId, null));

        when(userRepository.exists(userId)).thenReturn(true);
        when(eventRepository.getById(eventId)).thenReturn(null);
        assertThrows(DomainException.class,
                () -> service.validateSelectionEligibility(eventId, userId, null));

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(lotteryRepository.findByEventID(eventId)).thenReturn(null);
        service.validateSelectionEligibility(eventId, userId, null);

        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                eventId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );
        when(lotteryRepository.findByEventID(eventId)).thenReturn(lottery);

        assertThrows(DomainException.class,
                () -> service.validateSelectionEligibility(eventId, userId, "   "));

        lottery.registerMember(userId.toString(), 1, LocalDateTime.now());
        lottery.addWinner(userId.toString());
        service.validateSelectionEligibility(eventId, userId, null);

        String validCode = lottery.generateWinnerAccessCode(
                userId.toString(),
                LocalDateTime.now().plusMinutes(10)
        );
        service.validateSelectionEligibility(eventId, userId, validCode);

        assertThrows(DomainException.class,
                () -> service.validateSelectionEligibility(eventId, userId, "bad-code"));
    }

    @Test
    public void completePurchase_whenPolicyRejects_releasesTicketsAndDoesNotCharge() {
        UUID activePurchaseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        ActivePurchase activePurchase = mock(ActivePurchase.class);
        Event event = mock(Event.class);
        PurchasePolicy purchasePolicy = mock(PurchasePolicy.class);
        IPaymentGateway paymentGateway = mock(IPaymentGateway.class);
        ITicketingGateway ticketingGateway = mock(ITicketingGateway.class);
        User user = new User(userId, "buyer", "buyer@example.com", "hash", 25);
        Map<UUID, Float> tickets = Map.of(ticketId, 100f);

        when(purchaseRepository.findByID(activePurchaseId)).thenReturn(activePurchase);
        when(activePurchase.getActivePurchaseId()).thenReturn(activePurchaseId);
        when(activePurchase.getUserID()).thenReturn(userId);
        when(activePurchase.getEventID()).thenReturn(eventId);
        when(activePurchase.getTicketIDs()).thenReturn(tickets);
        when(activePurchase.getLastUpdate()).thenReturn(LocalDateTime.now());
        when(activePurchase.getMaxWaitTime()).thenReturn(10f);
        when(activePurchase.isExpired(any(LocalDateTime.class))).thenReturn(false);
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(userRepository.getUser(userId)).thenReturn(Optional.of(user));
        when(event.getPurchasePolicy()).thenReturn(purchasePolicy);
        when(purchasePolicy.validate(activePurchase, user, event))
                .thenThrow(new DomainException("blocked by policy"));

        service.setPaymentGateway(paymentGateway);
        service.setTicketingGateway(ticketingGateway);

        assertThrows(DomainException.class,
                () -> service.completePurchase(activePurchaseId, new PaymentDetails(), null));

        verify(event).releaseTickets(tickets);
        verifyNoInteractions(paymentGateway);
        verifyNoInteractions(ticketingGateway);
    }

    @Test
    public void completePurchase_paymentGatewayExceptionsAreTranslatedAndKeepReservation() {
        UUID activePurchaseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        ActivePurchase activePurchase = mockActivePurchase(activePurchaseId, userId, eventId, ticketId, 100f);
        Event event = mock(Event.class);
        User user = new User(userId, "buyer", "buyer@example.com", "hash", 25);
        IPaymentGateway paymentGateway = mock(IPaymentGateway.class);
        ITicketingGateway ticketingGateway = mock(ITicketingGateway.class);

        when(purchaseRepository.findByID(activePurchaseId)).thenReturn(activePurchase);
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(userRepository.getUser(userId)).thenReturn(Optional.of(user));
        when(event.getPurchasePolicy()).thenReturn(new PurchasePolicy());
        when(event.getDiscountPolicy()).thenReturn(null);
        when(event.getCompanyId()).thenReturn(null);

        service.setPaymentGateway(paymentGateway);
        service.setTicketingGateway(ticketingGateway);

        when(paymentGateway.pay(eq(userId), eq(100f), any(PaymentDetails.class)))
                .thenThrow(new IllegalArgumentException("bad card"));

        DomainException invalidDetails = assertThrows(DomainException.class,
                () -> service.completePurchase(activePurchaseId, new PaymentDetails(), null));
        assertEquals("bad card", invalidDetails.getMessage());
        verifyNoInteractions(ticketingGateway);
        verify(purchaseRepository, never()).deleteByID(activePurchaseId);

        reset(paymentGateway, ticketingGateway);
        service.setPaymentGateway(paymentGateway);
        service.setTicketingGateway(ticketingGateway);

        when(paymentGateway.pay(eq(userId), eq(100f), any(PaymentDetails.class)))
                .thenThrow(new RuntimeException("network down"));

        DomainException unavailable = assertThrows(DomainException.class,
                () -> service.completePurchase(activePurchaseId, new PaymentDetails(), null));
        assertTrue(unavailable.getMessage().contains("payment service is temporarily unavailable"));
        verifyNoInteractions(ticketingGateway);
        verify(purchaseRepository, never()).deleteByID(activePurchaseId);
    }

    @Test
    public void completePurchase_whenTicketingReturnsNoReferences_refundsAndDeletesActivePurchase() {
        UUID activePurchaseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        ActivePurchase activePurchase = mockActivePurchase(activePurchaseId, userId, eventId, ticketId, 100f);
        Event event = mock(Event.class);
        User user = new User(userId, "buyer", "buyer@example.com", "hash", 25);
        IPaymentGateway paymentGateway = mock(IPaymentGateway.class);
        ITicketingGateway ticketingGateway = mock(ITicketingGateway.class);
        Map<UUID, Float> tickets = activePurchase.getTicketIDs();

        when(purchaseRepository.findByID(activePurchaseId)).thenReturn(activePurchase);
        when(eventRepository.getById(eventId)).thenReturn(event);
        when(userRepository.getUser(userId)).thenReturn(Optional.of(user));
        when(event.getPurchasePolicy()).thenReturn(new PurchasePolicy());
        when(event.getDiscountPolicy()).thenReturn(null);
        when(event.getCompanyId()).thenReturn(null);
        when(paymentGateway.pay(eq(userId), eq(100f), any(PaymentDetails.class)))
                .thenReturn(PaymentResult.success(888));
        when(paymentGateway.refund(888)).thenReturn(true);
        when(ticketingGateway.issueTicketRefs(eq(userId), eq(eventId), anySet()))
                .thenReturn(new ArrayList<>());

        service.setPaymentGateway(paymentGateway);
        service.setTicketingGateway(ticketingGateway);

        DomainException exception = assertThrows(DomainException.class,
                () -> service.completePurchase(activePurchaseId, new PaymentDetails(), null));

        assertTrue(exception.getMessage().contains("has been refunded"));
        verify(paymentGateway).refund(888);
        verify(event).releaseTickets(tickets);
        verify(purchaseRepository).deleteByID(activePurchaseId);
        verify(historyRepository, never()).add(any(PurchaseHistory.class));
    }

    @Test
    public void registerToLottery_validationAndTicketAmountRuleBranches() {
        UUID eventId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Event event = event(eventId, companyId);
        event.addPurchasePolicy(Optional.empty(), Optional.of(2), Optional.of(3), Optional.empty(), true);
        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                eventId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );

        assertThrows(DomainException.class,
                () -> service.registerToLottery(null, memberId, 1));
        assertThrows(DomainException.class,
                () -> service.registerToLottery(eventId, null, 1));
        assertThrows(DomainException.class,
                () -> service.registerToLottery(eventId, memberId, 0));

        when(userRepository.getUser(memberId)).thenReturn(Optional.empty());
        assertThrows(DomainException.class,
                () -> service.registerToLottery(eventId, memberId, 1));

        User guest = new User(memberId);
        when(userRepository.getUser(memberId)).thenReturn(Optional.of(guest));
        assertThrows(DomainException.class,
                () -> service.registerToLottery(eventId, memberId, 1));

        User member = new User(memberId, "member", "member@example.com", "hash", 30);
        when(userRepository.getUser(memberId)).thenReturn(Optional.of(member));
        assertThrows(DomainException.class,
                () -> service.registerToLottery(eventId, memberId, 1));

        member.login();
        when(eventRepository.getById(eventId)).thenReturn(null);
        assertThrows(DomainException.class,
                () -> service.registerToLottery(eventId, memberId, 1));

        when(eventRepository.getById(eventId)).thenReturn(event);
        when(lotteryRepository.findByEventID(eventId)).thenReturn(null);
        assertThrows(DomainException.class,
                () -> service.registerToLottery(eventId, memberId, 1));

        when(lotteryRepository.findByEventID(eventId)).thenReturn(lottery);
        assertThrows(DomainException.class,
                () -> service.registerToLottery(eventId, memberId, 1));
        assertThrows(DomainException.class,
                () -> service.registerToLottery(eventId, memberId, 4));

        service.registerToLottery(eventId, memberId, 2);

        assertTrue(lottery.isRegistered(memberId.toString()));
        verify(lotteryRepository).save(lottery);
    }

    @Test
    public void drawLotteryForEvent_validationAndSuccessfulDrawBranches() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        LocalDateTime expiry = LocalDateTime.now().plusHours(1);

        assertThrows(DomainException.class,
                () -> service.drawLotteryForEvent(null, expiry));
        assertThrows(DomainException.class,
                () -> service.drawLotteryForEvent(eventId, null));

        when(eventRepository.getById(eventId)).thenReturn(null);
        assertThrows(DomainException.class,
                () -> service.drawLotteryForEvent(eventId, expiry));

        Event emptyEvent = event(eventId, companyId);
        when(eventRepository.getById(eventId)).thenReturn(emptyEvent);
        when(lotteryRepository.findByEventID(eventId)).thenReturn(null);
        assertThrows(DomainException.class,
                () -> service.drawLotteryForEvent(eventId, expiry));

        PuchaseLottery emptyLottery = new PuchaseLottery(
                UUID.randomUUID(), eventId, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
        when(lotteryRepository.findByEventID(eventId)).thenReturn(emptyLottery);
        assertThrows(DomainException.class,
                () -> service.drawLotteryForEvent(eventId, expiry));

        emptyLottery.registerMember("user-1", 1, LocalDateTime.now());
        assertThrows(DomainException.class,
                () -> service.drawLotteryForEvent(eventId, expiry));

        Event eventWithTicket = eventWithOneSittingTicket(eventId, companyId, areaId, ticketId);
        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(), eventId, LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
        lottery.registerMember("user-1", 1, LocalDateTime.now());
        when(eventRepository.getById(eventId)).thenReturn(eventWithTicket);
        when(lotteryRepository.findByEventID(eventId)).thenReturn(lottery);

        Map<String, String> codes = service.drawLotteryForEvent(eventId, expiry);

        assertEquals(1, codes.size());
        assertTrue(codes.containsKey("user-1"));
        verify(lotteryRepository).save(lottery);
    }

    @Test
    public void salesReportAndLotteryReadFlags_coverMissingAndPositiveCases() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        String ownerEmail = "owner@example.com";

        when(companyRepository.findByID(companyId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.getSalesReportForOwner(ownerEmail, companyId));

        Company company = new Company(ownerEmail, "Acme", DiscountType.ALL);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail(ownerEmail)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.getSalesReportForOwner(ownerEmail, companyId));

        User owner = mock(User.class);
        when(userRepository.findByEmail(ownerEmail)).thenReturn(Optional.of(owner));
        when(owner.getMyEventIdsOfCompany(companyId)).thenReturn(List.of(eventId));
        PurchaseHistory history = new PurchaseHistory(
                ownerId,
                List.of(ticketId),
                eventId,
                new Payment(150f, "paid", 12),
                "TIX-12"
        );
        when(historyRepository.getByEventId(eventId)).thenReturn(List.of(history));

        SalesReport report = service.getSalesReportForOwner(ownerEmail, companyId);

        assertNotNull(report);

        UUID lotteryEventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        assertFalse(service.areLotteryWinnersDrawn(null));
        assertFalse(service.isUserRegisteredToLottery(null, userId));
        assertFalse(service.isUserRegisteredToLottery(lotteryEventId, null));
        assertFalse(service.isUserWinner(null, userId));
        assertFalse(service.isUserWinner(lotteryEventId, null));
        assertFalse(service.isLotteryEvent(lotteryEventId));

        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                lotteryEventId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );
        lottery.registerMember(userId.toString(), 1, LocalDateTime.now());
        lottery.addWinner(userId.toString());
        lottery.generateWinnerAccessCode(userId.toString(), LocalDateTime.now().plusMinutes(5));
        when(lotteryRepository.findByEventID(lotteryEventId)).thenReturn(lottery);

        assertTrue(service.isLotteryEvent(lotteryEventId));
        assertTrue(service.areLotteryWinnersDrawn(lotteryEventId));
        assertTrue(service.isUserRegisteredToLottery(lotteryEventId, userId));
        assertTrue(service.isUserWinner(lotteryEventId, userId));
    }

    private ActivePurchase mockActivePurchase(UUID activePurchaseId,
                                              UUID userId,
                                              UUID eventId,
                                              UUID ticketId,
                                              float price) {
        ActivePurchase activePurchase = mock(ActivePurchase.class);
        Map<UUID, Float> tickets = Map.of(ticketId, price);

        when(activePurchase.getActivePurchaseId()).thenReturn(activePurchaseId);
        when(activePurchase.getUserID()).thenReturn(userId);
        when(activePurchase.getEventID()).thenReturn(eventId);
        when(activePurchase.getTicketIDs()).thenReturn(tickets);
        when(activePurchase.getPrice()).thenReturn(price);
        when(activePurchase.getLastUpdate()).thenReturn(LocalDateTime.now());
        when(activePurchase.getMaxWaitTime()).thenReturn(10f);
        when(activePurchase.isExpired(any(LocalDateTime.class))).thenReturn(false);

        return activePurchase;
    }
}
