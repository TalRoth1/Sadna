package org.example.DomainLayer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.SittingTicket;
import org.example.DomainLayer.EventAggregate.TicketStatus;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.PurchaseHistoryAggregate.Payment;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.User;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.mock;

public class PurchaseDomainServiceTest {

    private FakeHistoryRepository historyRepository;
    private FakeEventRepository eventRepository;
    private FakeCompanyRepository companyRepository;
    private FakeUserRepository userRepository;

    private IPurchaseRepository purchaseRepository;
    private ILotteryRepository lotteryRepository;

    private PurchaseDomainService purchaseDomainService;

    @Before
    public void setUp() {
        historyRepository = new FakeHistoryRepository();
        eventRepository = new FakeEventRepository();
        companyRepository = new FakeCompanyRepository();
        userRepository = new FakeUserRepository();

        purchaseRepository = new FakePurchaseRepository();
        lotteryRepository = new FakeLotteryRepository();

        purchaseDomainService = new PurchaseDomainService(
                historyRepository,
                eventRepository,
                purchaseRepository,
                companyRepository,
                userRepository,
                lotteryRepository
        );
    }

    private Event eventWithOneSittingTicket(UUID eventId, UUID companyId, UUID areaId, UUID ticketId) {
        Event event = event(eventId, companyId);
        event.getLayout().addArea(new SittingArea(areaId, 100f));
        event.addTicket(new SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        return event;
    }

    @Test
    public void selectSittingTicketsWithLotteryCode_whenUserNotRegisteredToLottery_throwsAndDoesNotReserveTicket() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Event event = eventWithOneSittingTicket(eventId, companyId, areaId, ticketId);
        eventRepository.save(event);
        userRepository.add(new User(userId, "user", "user@mail.com", "pass", 20));

        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                eventId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );
        lotteryRepository.save(lottery);

        assertThrows(DomainException.class, () ->
                purchaseDomainService.selectSittingTicketsWithLotteryCode(
                        eventId,
                        List.of(ticketId),
                        userId,
                        false,
                        "some-code"
                )
        );

        assertNull(purchaseRepository.findByUserID(userId));
        assertEquals(TicketStatus.AVAILABLE, event.getTicket(ticketId).getStatus());
    }

    @Test
    public void selectSittingTicketsWithLotteryCode_whenUserRegisteredButDidNotWin_throwsAndDoesNotReserveTicket() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Event event = eventWithOneSittingTicket(eventId, companyId, areaId, ticketId);
        eventRepository.save(event);
        userRepository.add(new User(userId, "user", "user@mail.com", "pass", 20));

        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                eventId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );

        lottery.registerMember(userId.toString(), 1, LocalDateTime.now());
        lotteryRepository.save(lottery);

        assertThrows(DomainException.class, () ->
                purchaseDomainService.selectSittingTicketsWithLotteryCode(
                        eventId,
                        List.of(ticketId),
                        userId,
                        false,
                        "some-code"
                )
        );

        assertNull(purchaseRepository.findByUserID(userId));
        assertEquals(TicketStatus.AVAILABLE, event.getTicket(ticketId).getStatus());
    }

    @Test
    public void selectSittingTicketsWithLotteryCode_whenWinnerCodeExpired_throwsAndDoesNotReserveTicket() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Event event = eventWithOneSittingTicket(eventId, companyId, areaId, ticketId);
        eventRepository.save(event);
        userRepository.add(new User(userId, "user", "user@mail.com", "pass", 20));

        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                eventId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );

        lottery.registerMember(userId.toString(), 1, LocalDateTime.now());
        lottery.addWinner(userId.toString());

        String expiredCode = lottery.generateWinnerAccessCode(
                userId.toString(),
                LocalDateTime.now().minusMinutes(1)
        );

        lotteryRepository.save(lottery);

        assertThrows(DomainException.class, () ->
                purchaseDomainService.selectSittingTicketsWithLotteryCode(
                        eventId,
                        List.of(ticketId),
                        userId,
                        false,
                        expiredCode
                )
        );

        assertNull(purchaseRepository.findByUserID(userId));
        assertEquals(TicketStatus.AVAILABLE, event.getTicket(ticketId).getStatus());
    }
    @Test
    public void selectSittingTicketsWithLotteryCode_whenWinnerHasValidCode_createsActivePurchaseAndReservesTicket() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Event event = eventWithOneSittingTicket(eventId, companyId, areaId, ticketId);
        eventRepository.save(event);
        userRepository.add(new User(userId, "user", "user@mail.com", "pass", 20));

        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                eventId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );

        lottery.registerMember(userId.toString(), 1, LocalDateTime.now());
        lottery.addWinner(userId.toString());

        String accessCode = lottery.generateWinnerAccessCode(
                userId.toString(),
                LocalDateTime.now().plusMinutes(10)
        );

        lotteryRepository.save(lottery);

        purchaseDomainService.selectSittingTicketsWithLotteryCode(
                eventId,
                List.of(ticketId),
                userId,
                false,
                accessCode
        );

        ActivePurchase activePurchase = purchaseRepository.findByUserID(userId);

        assertNotNull(activePurchase);
        assertEquals(eventId, activePurchase.getEventID());
        assertTrue(activePurchase.getTicketIDs().containsKey(ticketId));
        assertEquals(TicketStatus.RESERVED, event.getTicket(ticketId).getStatus());
    }

    @Test
    public void completePurchase_whenPaymentFails_preservesInvariants() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);

        event.getLayout().addArea(
                new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f)
        );

        event.addTicket(
                new org.example.DomainLayer.EventAggregate.SittingTicket(
                        ticketId,
                        eventId,
                        areaId,
                        100f,
                        1,
                        1
                )
        );

        eventRepository.save(event);

        userRepository.add(
                new User(userId, "user", "mail", "pass", 20)
        );

        purchaseDomainService.selectSittingTickets(
                eventId,
                List.of(ticketId),
                userId,
                true
        );

        ActivePurchase purchase =
                purchaseRepository.findByUserID(userId);

        final boolean[] ticketingWasCalled = {false};

        purchaseDomainService.setPaymentGateway(
                (uid, amount, details) -> false
        );

        purchaseDomainService.setTicketingGateway(
                (uid, eid, ticketIds) -> ticketingWasCalled[0] = true
        );

        assertThrows(
                DomainException.class,
                () -> purchaseDomainService.completePurchase(
                        purchase.getActivePurchaseId(),
                        new org.example.ApplicationLayer.PaymentDetails(),
                        null
                )
        );

        // ticketing לא נקרא
        assertFalse(ticketingWasCalled[0]);

        // ה-active purchase עדיין קיים
        assertNotNull(
                purchaseRepository.findByID(
                        purchase.getActivePurchaseId()
                )
        );

        // הכרטיס לא נמכר
        assertEquals(
                org.example.DomainLayer.EventAggregate.TicketStatus.RESERVED,
                event.getTicket(ticketId).getStatus()
        );

        // לא נוספה היסטוריה
        assertTrue(
                historyRepository.getByUserId(userId).isEmpty()
        );
    }


    @Test
    public void getAllHistory_whenPurchasesExist_returnsAllPurchasesWithoutFiltering() {
        PurchaseHistory firstPurchase = purchase(UUID.randomUUID(), UUID.randomUUID(), 100);
        PurchaseHistory secondPurchase = purchase(UUID.randomUUID(), UUID.randomUUID(), 200);
        PurchaseHistory thirdPurchase = purchase(UUID.randomUUID(), UUID.randomUUID(), 300);

        historyRepository.add(firstPurchase);
        historyRepository.add(secondPurchase);
        historyRepository.add(thirdPurchase);

        List<PurchaseHistory> result = purchaseDomainService.getAllHistory();

        assertEquals(3, result.size());
        assertTrue(result.contains(firstPurchase));
        assertTrue(result.contains(secondPurchase));
        assertTrue(result.contains(thirdPurchase));
    }

    @Test
    public void getHistoryByUser_whenMultipleUsersHavePurchases_returnsOnlyRequestedUserPurchases() {
        UUID requestedUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        PurchaseHistory firstRequestedUserPurchase = purchase(requestedUserId, UUID.randomUUID(), 100);
        PurchaseHistory secondRequestedUserPurchase = purchase(requestedUserId, UUID.randomUUID(), 150);
        PurchaseHistory otherUserPurchase = purchase(otherUserId, UUID.randomUUID(), 300);

        historyRepository.add(firstRequestedUserPurchase);
        historyRepository.add(secondRequestedUserPurchase);
        historyRepository.add(otherUserPurchase);

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByUser(requestedUserId);

        assertEquals(2, result.size());
        assertTrue(result.contains(firstRequestedUserPurchase));
        assertTrue(result.contains(secondRequestedUserPurchase));
        assertFalse(result.contains(otherUserPurchase));
    }

    @Test
    public void getHistoryByUser_whenUserHasNoPurchases_returnsEmptyList() {
        UUID requestedUserId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        historyRepository.add(purchase(otherUserId, UUID.randomUUID(), 80));
        historyRepository.add(purchase(otherUserId, UUID.randomUUID(), 120));

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByUser(requestedUserId);

        assertTrue(result.isEmpty());
    }

    @Test
    public void getHistoryByEvent_whenMultipleEventsHavePurchases_returnsOnlyRequestedEventPurchases() {
        UUID requestedEventId = UUID.randomUUID();
        UUID otherEventId = UUID.randomUUID();

        PurchaseHistory firstRequestedEventPurchase = purchase(UUID.randomUUID(), requestedEventId, 100);
        PurchaseHistory secondRequestedEventPurchase = purchase(UUID.randomUUID(), requestedEventId, 200);
        PurchaseHistory otherEventPurchase = purchase(UUID.randomUUID(), otherEventId, 300);

        historyRepository.add(firstRequestedEventPurchase);
        historyRepository.add(secondRequestedEventPurchase);
        historyRepository.add(otherEventPurchase);

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByEvent(requestedEventId);

        assertEquals(2, result.size());
        assertTrue(result.contains(firstRequestedEventPurchase));
        assertTrue(result.contains(secondRequestedEventPurchase));
        assertFalse(result.contains(otherEventPurchase));
    }

    @Test
    public void getHistoryByEvent_whenEventHasNoPurchases_returnsEmptyList() {
        UUID requestedEventId = UUID.randomUUID();

        historyRepository.add(purchase(UUID.randomUUID(), UUID.randomUUID(), 75));
        historyRepository.add(purchase(UUID.randomUUID(), UUID.randomUUID(), 125));

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByEvent(requestedEventId);

        assertTrue(result.isEmpty());
    }

    @Test
    public void getHistoryByCompany_whenCompanyHasMultipleEvents_returnsPurchasesOnlyForThatCompanyEvents() {
        UUID requestedCompanyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();

        UUID firstCompanyEventId = UUID.randomUUID();
        UUID secondCompanyEventId = UUID.randomUUID();
        UUID otherCompanyEventId = UUID.randomUUID();

        Event firstCompanyEvent = event(firstCompanyEventId, requestedCompanyId);
        Event secondCompanyEvent = event(secondCompanyEventId, requestedCompanyId);
        Event otherCompanyEvent = event(otherCompanyEventId, otherCompanyId);

        eventRepository.save(firstCompanyEvent);
        eventRepository.save(secondCompanyEvent);
        eventRepository.save(otherCompanyEvent);

        PurchaseHistory firstCompanyPurchase = purchase(UUID.randomUUID(), firstCompanyEventId, 100);
        PurchaseHistory secondCompanyPurchase = purchase(UUID.randomUUID(), secondCompanyEventId, 200);
        PurchaseHistory otherCompanyPurchase = purchase(UUID.randomUUID(), otherCompanyEventId, 300);

        historyRepository.add(firstCompanyPurchase);
        historyRepository.add(secondCompanyPurchase);
        historyRepository.add(otherCompanyPurchase);

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByCompany(requestedCompanyId);

        assertEquals(2, result.size());
        assertTrue(result.contains(firstCompanyPurchase));
        assertTrue(result.contains(secondCompanyPurchase));
        assertFalse(result.contains(otherCompanyPurchase));
    }

    @Test
    public void getHistoryByCompany_whenPurchaseEventDoesNotExist_ignoresThatPurchase() {
        UUID requestedCompanyId = UUID.randomUUID();

        UUID existingEventId = UUID.randomUUID();
        UUID missingEventId = UUID.randomUUID();

        eventRepository.save(event(existingEventId, requestedCompanyId));

        PurchaseHistory validPurchase = purchase(UUID.randomUUID(), existingEventId, 100);
        PurchaseHistory purchaseWithMissingEvent = purchase(UUID.randomUUID(), missingEventId, 200);

        historyRepository.add(validPurchase);
        historyRepository.add(purchaseWithMissingEvent);

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByCompany(requestedCompanyId);

        assertEquals(1, result.size());
        assertTrue(result.contains(validPurchase));
        assertFalse(result.contains(purchaseWithMissingEvent));
    }

    @Test
    public void getHistoryByCompany_whenCompanyHasNoPurchases_returnsEmptyList() {
        UUID requestedCompanyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();

        UUID otherCompanyEventId = UUID.randomUUID();

        eventRepository.save(event(otherCompanyEventId, otherCompanyId));
        historyRepository.add(purchase(UUID.randomUUID(), otherCompanyEventId, 250));

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByCompany(requestedCompanyId);

        assertTrue(result.isEmpty());
    }

    @Test
    public void getHistoryByCompany_whenCompanyIdHasSameValueButDifferentObject_stillReturnsMatchingPurchases() {
        UUID requestedCompanyId = UUID.randomUUID();
        UUID sameCompanyIdDifferentObject = UUID.fromString(requestedCompanyId.toString());

        UUID eventId = UUID.randomUUID();

        eventRepository.save(event(eventId, sameCompanyIdDifferentObject));

        PurchaseHistory purchase = purchase(UUID.randomUUID(), eventId, 400);
        historyRepository.add(purchase);

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByCompany(requestedCompanyId);

        assertEquals(1, result.size());
        assertTrue(result.contains(purchase));
    }

    @Test
    public void getPurchaseHistoryForMember_whenMemberHasPurchases_returnsOnlyThatMemberPurchases() {
        UUID memberId = UUID.randomUUID();
        UUID otherMemberId = UUID.randomUUID();

        PurchaseHistory firstMemberPurchase = purchase(memberId, UUID.randomUUID(), 100);
        PurchaseHistory secondMemberPurchase = purchase(memberId, UUID.randomUUID(), 200);
        PurchaseHistory otherMemberPurchase = purchase(otherMemberId, UUID.randomUUID(), 300);

        historyRepository.add(firstMemberPurchase);
        historyRepository.add(secondMemberPurchase);
        historyRepository.add(otherMemberPurchase);

        List<PurchaseHistory> result = purchaseDomainService.getPurchaseHistoryForMember(memberId);

        assertEquals(2, result.size());
        assertTrue(result.contains(firstMemberPurchase));
        assertTrue(result.contains(secondMemberPurchase));
        assertFalse(result.contains(otherMemberPurchase));
    }

    @Test
    public void getPurchaseHistoryForMember_whenMemberHasNoPurchases_returnsEmptyList() {
        UUID memberId = UUID.randomUUID();

        historyRepository.add(purchase(UUID.randomUUID(), UUID.randomUUID(), 50));

        List<PurchaseHistory> result = purchaseDomainService.getPurchaseHistoryForMember(memberId);

        assertTrue(result.isEmpty());
    }

    @Test
    public void eventExists_whenEventExists_returnsTrue() {
        UUID eventId = UUID.randomUUID();

        eventRepository.save(event(eventId, UUID.randomUUID()));

        assertTrue(purchaseDomainService.eventExists(eventId));
    }

    @Test
    public void eventExists_whenEventDoesNotExist_returnsFalse() {
        assertFalse(purchaseDomainService.eventExists(UUID.randomUUID()));
    }

    @Test
    public void completePurchase_whenPaymentFails_doesNotCallTicketingGateway() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);

        event.getLayout().addArea(
                new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f)
        );

        event.addTicket(
                new org.example.DomainLayer.EventAggregate.SittingTicket(
                        ticketId,
                        eventId,
                        areaId,
                        100f,
                        1,
                        1
                )
        );

        eventRepository.save(event);

        userRepository.add(
                new User(userId, "user", "mail", "pass", 20)
        );

        purchaseDomainService.selectSittingTickets(
                eventId,
                List.of(ticketId),
                userId,
                true
        );

        ActivePurchase purchase =
                purchaseRepository.findByUserID(userId);

        final boolean[] ticketingWasCalled = {false};

        purchaseDomainService.setPaymentGateway(
                (uid, amount, details) -> false
        );

        purchaseDomainService.setTicketingGateway(
                (uid, eid, ticketIds) -> ticketingWasCalled[0] = true
        );

        assertThrows(
                DomainException.class,
                () -> purchaseDomainService.completePurchase(
                        purchase.getActivePurchaseId(),
                        new org.example.ApplicationLayer.PaymentDetails(),
                        null
                )
        );

        assertFalse(ticketingWasCalled[0]);
    }



    @Test
    public void isCompanyOwnerOfEvent_whenUserIsOwnerOfEventCompany_returnsTrue() {
        String ownerName = "owner";
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        Company company = mock(Company.class);

        eventRepository.save(event);
        companyRepository.save(companyId, company);

        UUID ownerId = UUID.randomUUID();
        org.example.DomainLayer.UserAggregate.User ownerUser =
            new org.example.DomainLayer.UserAggregate.User(ownerId, ownerName, ownerName, "hash", 40);
        ownerUser.getCompanyRoles().put(companyId, new org.example.DomainLayer.UserAggregate.CompanyFounder(ownerName));
        userRepository.add(ownerUser);

        assertTrue(purchaseDomainService.isCompanyOwnerOfEvent(ownerName, eventId));
    }

    @Test
    public void isCompanyOwnerOfEvent_whenUserIsNotOwnerOfEventCompany_returnsFalse() {
        String ownerName = "notOwner";
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        Company company = mock(Company.class);

        eventRepository.save(event);
        companyRepository.save(companyId, company);

        // no user added to fake repository -> isCompanyOwner should return false

        assertFalse(purchaseDomainService.isCompanyOwnerOfEvent(ownerName, eventId));
    }

    @Test
    public void isCompanyOwnerOfEvent_whenEventDoesNotExist_returnsFalse() {
        assertFalse(purchaseDomainService.isCompanyOwnerOfEvent("owner", UUID.randomUUID()));
    }

    @Test
    public void isCompanyOwnerOfEvent_whenCompanyDoesNotExist_returnsFalse() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        eventRepository.save(event(eventId, companyId));

        assertFalse(purchaseDomainService.isCompanyOwnerOfEvent("owner", eventId));
    }

    @Test
    public void getAllHistory_whenNoPurchasesExist_returnsEmptyList() {
        List<PurchaseHistory> result = purchaseDomainService.getAllHistory();

        assertTrue(result.isEmpty());
    }

    @Test
    public void getHistoryByCompany_whenSomePurchasesAreForMissingOrOtherCompanyEvents_returnsOnlyMatchingCompanyPurchases() {
        UUID requestedCompanyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();

        UUID matchingEventId = UUID.randomUUID();
        UUID otherCompanyEventId = UUID.randomUUID();
        UUID missingEventId = UUID.randomUUID();

        eventRepository.save(event(matchingEventId, requestedCompanyId));
        eventRepository.save(event(otherCompanyEventId, otherCompanyId));

        PurchaseHistory matchingPurchase = purchase(UUID.randomUUID(), matchingEventId, 100);
        PurchaseHistory otherCompanyPurchase = purchase(UUID.randomUUID(), otherCompanyEventId, 200);
        PurchaseHistory missingEventPurchase = purchase(UUID.randomUUID(), missingEventId, 300);

        historyRepository.add(matchingPurchase);
        historyRepository.add(otherCompanyPurchase);
        historyRepository.add(missingEventPurchase);

        List<PurchaseHistory> result = purchaseDomainService.getHistoryByCompany(requestedCompanyId);

        assertEquals(1, result.size());
        assertTrue(result.contains(matchingPurchase));
        assertFalse(result.contains(otherCompanyPurchase));
        assertFalse(result.contains(missingEventPurchase));
    }

    @Test
    public void getHistoryByCompany_whenCompanyIdIsNull_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                purchaseDomainService.getHistoryByCompany(null)
        );
    }

    private PurchaseHistory purchase(UUID userId, UUID eventId, double total) {
        List<UUID> ticketIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        Payment payment = new Payment(total, "test-payment");

        return new PurchaseHistory(userId, ticketIds, eventId, payment);
    }

    private Event event(UUID eventId, UUID companyId) {
        return new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(1),
                "Tel Aviv",
                "Artist",
                "Concert",
                EventStatus.ACTIVE
        );
    }

    @Test
    public void selectSittingTickets_whenTicketsAreAvailable_createsActivePurchaseAndReservesTickets() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f));
        event.addTicket(new org.example.DomainLayer.EventAggregate.SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        eventRepository.save(event);
        userRepository.add(new User(userId, "user", "user@mail.com", "pass", 20));

        purchaseDomainService.selectSittingTickets(eventId, List.of(ticketId), userId, true);

        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        assertNotNull(purchase);
        assertEquals(eventId, purchase.getEventID());
        assertTrue(purchase.getTicketIDs().containsKey(ticketId));
        assertTrue(purchase.getGuestAgeConfirmed());
        assertEquals(org.example.DomainLayer.EventAggregate.TicketStatus.RESERVED,
                event.getTicket(ticketId).getStatus());
    }

    @Test
    public void selectSittingTickets_whenUserAlreadyHasActivePurchase_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        ActivePurchase existingPurchase = new ActivePurchase(
                userId,
                eventId,
                new LinkedHashMap<>(),
                LocalDateTime.now().plusMinutes(10)
        );

        purchaseRepository.save(existingPurchase);

        assertThrows(DomainException.class, () ->
                purchaseDomainService.selectSittingTickets(
                        eventId,
                        List.of(UUID.randomUUID()),
                        userId,
                        false
                )
        );
    }

    @Test
    public void selectStandingTickets_whenTicketsAreAvailable_createsActivePurchaseAndReservesTickets() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.StandingArea(areaId, 100f));
        event.addStandingTickets(areaId, 2);
        eventRepository.save(event);
        userRepository.add(new User(userId, "user", "mail", "pass", 20));

        purchaseDomainService.selectStandingTickets(eventId, 1, userId, areaId, false);

        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        assertNotNull(purchase);
        assertEquals(eventId, purchase.getEventID());
        assertEquals(1, purchase.getTicketIDs().size());
        assertFalse(purchase.getGuestAgeConfirmed());
    }



    @Test
    public void completePurchase_whenPaymentAndTicketingSucceed_sellsTicketsAndDeletesActivePurchase() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f));
        event.addTicket(new org.example.DomainLayer.EventAggregate.SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        eventRepository.save(event);

        userRepository.add(new User(userId, "user", "mail", "pass", 20));
        purchaseDomainService.selectSittingTickets(eventId, List.of(ticketId), userId, true);
        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        purchaseDomainService.setPaymentGateway((uid, amount, details) -> true);
        purchaseDomainService.setTicketingGateway((uid, eid, ticketIds) -> {});

        purchaseDomainService.completePurchase(
                purchase.getActivePurchaseId(),
                new org.example.ApplicationLayer.PaymentDetails(),
                null
        );

        assertNull(purchaseRepository.findByID(purchase.getActivePurchaseId()));
        assertEquals(org.example.DomainLayer.EventAggregate.TicketStatus.SOLD,
                event.getTicket(ticketId).getStatus());
    }

    @Test
    public void completePurchase_whenPaymentAndTicketingSucceed_addsPurchaseToHistory() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f));
        event.addTicket(new org.example.DomainLayer.EventAggregate.SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        eventRepository.save(event);

        userRepository.add(new User(userId, "user", "mail", "pass", 20));

        purchaseDomainService.selectSittingTickets(eventId, List.of(ticketId), userId, true);
        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        purchaseDomainService.setPaymentGateway((uid, amount, details) -> true);
        purchaseDomainService.setTicketingGateway((uid, eid, ticketIds) -> {});

        purchaseDomainService.completePurchase(
                purchase.getActivePurchaseId(),
                new org.example.ApplicationLayer.PaymentDetails(),
                null
        );

        List<PurchaseHistory> history = historyRepository.getByUserId(userId);

        assertEquals(1, history.size());
        assertEquals(userId, history.get(0).getUserId());
        assertEquals(eventId, history.get(0).getEventId());
        assertTrue(history.get(0).getTicketIds().contains(ticketId));
    }

    @Test
    public void completePurchase_whenPaymentFails_doesNotAddPurchaseToHistory() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f));
        event.addTicket(new org.example.DomainLayer.EventAggregate.SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        eventRepository.save(event);

        userRepository.add(new User(userId, "user", "mail", "pass", 20));

        purchaseDomainService.selectSittingTickets(eventId, List.of(ticketId), userId, true);
        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        purchaseDomainService.setPaymentGateway((uid, amount, details) -> false);
        purchaseDomainService.setTicketingGateway((uid, eid, ticketIds) ->
                fail("Ticketing should not be called when payment fails")
        );

        assertThrows(DomainException.class, () ->
                purchaseDomainService.completePurchase(
                        purchase.getActivePurchaseId(),
                        new org.example.ApplicationLayer.PaymentDetails(),
                        null
                )
        );

        assertTrue(historyRepository.getByUserId(userId).isEmpty());
    }

    @Test
    public void completePurchase_whenTicketingFails_doesNotAddPurchaseToHistory() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f));
        event.addTicket(new org.example.DomainLayer.EventAggregate.SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        eventRepository.save(event);

        userRepository.add(new User(userId, "user", "mail", "pass", 20));

        purchaseDomainService.selectSittingTickets(eventId, List.of(ticketId), userId, true);
        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        purchaseDomainService.setPaymentGateway((uid, amount, details) -> true);
        purchaseDomainService.setTicketingGateway((uid, eid, ticketIds) -> {
            throw new DomainException("Ticketing failed");
        });

        assertThrows(DomainException.class, () ->
                purchaseDomainService.completePurchase(
                        purchase.getActivePurchaseId(),
                        new org.example.ApplicationLayer.PaymentDetails(),
                        null
                )
        );

        assertTrue(historyRepository.getByUserId(userId).isEmpty());
    }

    @Test
    public void completePurchase_whenPaymentFails_throwsExceptionAndKeepsPurchaseActive() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f));
        event.addTicket(new org.example.DomainLayer.EventAggregate.SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        eventRepository.save(event);

        userRepository.add(new User(userId, "user", "mail", "pass", 20));

        purchaseDomainService.selectSittingTickets(eventId, List.of(ticketId), userId, true);
        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        purchaseDomainService.setPaymentGateway((uid, amount, details) -> false);
        purchaseDomainService.setTicketingGateway((uid, eid, ticketIds) -> fail("Ticketing should not be called when payment fails"));

        assertThrows(DomainException.class, () ->
                purchaseDomainService.completePurchase(
                        purchase.getActivePurchaseId(),
                        new org.example.ApplicationLayer.PaymentDetails(),
                        null
                )
        );

        assertNotNull(purchaseRepository.findByID(purchase.getActivePurchaseId()));
        assertEquals(org.example.DomainLayer.EventAggregate.TicketStatus.RESERVED,
                event.getTicket(ticketId).getStatus());
    }

    @Test
    public void updateActivePurchaseSittingTickets_whenNewTicketAvailable_replacesTickets() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();

        UUID oldTicketId = UUID.randomUUID();
        UUID newTicketId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f));
        event.addTicket(new org.example.DomainLayer.EventAggregate.SittingTicket(oldTicketId, eventId, areaId, 100f, 1, 1));
        event.addTicket(new org.example.DomainLayer.EventAggregate.SittingTicket(newTicketId, eventId, areaId, 120f, 1, 2));
        eventRepository.save(event);

        userRepository.add(new User(userId, "user", "mail", "pass", 20));

        purchaseDomainService.selectSittingTickets(eventId, List.of(oldTicketId), userId, false);
        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        purchaseDomainService.updateActivePurchaseSittingTickets(
                purchase.getActivePurchaseId(),
                List.of(newTicketId)
        );

        ActivePurchase updated = purchaseRepository.findByID(purchase.getActivePurchaseId());

        assertFalse(updated.getTicketIDs().containsKey(oldTicketId));
        assertTrue(updated.getTicketIDs().containsKey(newTicketId));
        assertEquals(org.example.DomainLayer.EventAggregate.TicketStatus.AVAILABLE,
                event.getTicket(oldTicketId).getStatus());
        assertEquals(org.example.DomainLayer.EventAggregate.TicketStatus.RESERVED,
                event.getTicket(newTicketId).getStatus());
    }

    @Test
    public void updateActivePurchaseStandingTickets_whenNewAmountAvailable_replacesTickets() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.StandingArea(areaId, 100f));
        event.addStandingTickets(areaId, 3);
        eventRepository.save(event);

        userRepository.add(new User(userId, "user", "mail", "pass", 20));

        purchaseDomainService.selectStandingTickets(eventId, 1, userId, areaId, false);
        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        purchaseDomainService.updateActivePurchaseStandingTickets(
                purchase.getActivePurchaseId(),
                2,
                areaId
        );

        ActivePurchase updated = purchaseRepository.findByID(purchase.getActivePurchaseId());

        assertEquals(2, updated.getTicketIDs().size());
    }

    @Test
    public void cancelActivePurchase_whenPurchaseExists_releasesTicketsAndDeletesPurchase() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f));
        event.addTicket(new org.example.DomainLayer.EventAggregate.SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        eventRepository.save(event);

        userRepository.add(new User(userId, "user", "mail", "pass", 20));

        purchaseDomainService.selectSittingTickets(eventId, List.of(ticketId), userId, false);
        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        purchaseDomainService.cancelActivePurchase(purchase.getActivePurchaseId());

        assertNull(purchaseRepository.findByID(purchase.getActivePurchaseId()));
        assertEquals(org.example.DomainLayer.EventAggregate.TicketStatus.AVAILABLE,
                event.getTicket(ticketId).getStatus());
    }

    @Test
    public void cancelActivePurchase_whenPurchaseDoesNotExist_throwsException() {
        assertThrows(DomainException.class, () ->
                purchaseDomainService.cancelActivePurchase(UUID.randomUUID())
        );
    }

    @Test
    public void viewActivePurchase_whenPurchaseExistsAndNotExpired_returnsPurchaseAndUpdatesLastUpdate() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f));
        event.addTicket(new org.example.DomainLayer.EventAggregate.SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        eventRepository.save(event);

        userRepository.add(new User(userId, "user", "mail", "pass", 20));

        purchaseDomainService.selectSittingTickets(eventId, List.of(ticketId), userId, false);
        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        LocalDateTime before = purchase.getLastUpdate();

        Thread.sleep(2);

        ActivePurchase result = purchaseDomainService.viewActivePurchase(purchase.getActivePurchaseId());

        assertSame(purchase, result);
        assertTrue(result.getLastUpdate().isAfter(before));
    }

    @Test
    public void viewActivePurchase_whenPurchaseDoesNotExist_throwsException() {
        assertThrows(DomainException.class, () ->
                purchaseDomainService.viewActivePurchase(UUID.randomUUID())
        );
    }

    @Test
    public void setPaymentGateway_whenCalled_allowsCompletePurchaseToUseGateway() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f));
        event.addTicket(new org.example.DomainLayer.EventAggregate.SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        eventRepository.save(event);

        userRepository.add(new User(userId, "user", "mail", "pass", 20));

        purchaseDomainService.selectSittingTickets(eventId, List.of(ticketId), userId, false);
        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        purchaseDomainService.setPaymentGateway((uid, amount, details) -> true);
        purchaseDomainService.setTicketingGateway((uid, eid, ticketIds) -> {});

        purchaseDomainService.completePurchase(
                purchase.getActivePurchaseId(),
                new org.example.ApplicationLayer.PaymentDetails(),
                null
        );

        assertNull(purchaseRepository.findByID(purchase.getActivePurchaseId()));
    }

    @Test
    public void setTicketingGateway_whenCalled_allowsCompletePurchaseToIssueTickets() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);
        event.getLayout().addArea(new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f));
        event.addTicket(new org.example.DomainLayer.EventAggregate.SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));
        eventRepository.save(event);

        userRepository.add(new User(userId, "user", "mail", "pass", 20));

        purchaseDomainService.selectSittingTickets(eventId, List.of(ticketId), userId, false);
        ActivePurchase purchase = purchaseRepository.findByUserID(userId);

        final boolean[] ticketingWasCalled = {false};

        purchaseDomainService.setPaymentGateway((uid, amount, details) -> true);
        purchaseDomainService.setTicketingGateway((uid, eid, ticketIds) -> ticketingWasCalled[0] = true);

        purchaseDomainService.completePurchase(
                purchase.getActivePurchaseId(),
                new org.example.ApplicationLayer.PaymentDetails(),
                null
        );

        assertTrue(ticketingWasCalled[0]);
    }

    @Test
    public void selectSittingTickets_whenTicketIsNotAvailable_throwsException() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID firstUserId = UUID.randomUUID();
        UUID secondUserId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Event event = event(eventId, companyId);

        event.getLayout().addArea(
                new SittingArea(areaId, 100f)
        );

        SittingTicket ticket = new SittingTicket(
                ticketId,
                eventId,
                areaId,
                100f,
                1,
                1
        );

        event.addTicket(ticket);

        eventRepository.save(event);
        userRepository.add(new User(firstUserId, "user1", "user1@mail.com", "pass", 20));
        userRepository.add(new User(secondUserId, "user2", "user2@mail.com", "pass", 20));

        // משתמש ראשון משריין
        purchaseDomainService.selectSittingTickets(
                eventId,
                List.of(ticketId),
                firstUserId,
                true
        );

        // משתמש שני מנסה לשריין אותו כרטיס
        assertThrows(
                DomainException.class,
                () -> purchaseDomainService.selectSittingTickets(
                        eventId,
                        List.of(ticketId),
                        secondUserId,
                        true
                )
        );

        // עדיין RESERVED
        assertEquals(
                TicketStatus.RESERVED,
                event.getTicket(ticketId).getStatus()
        );

        // למשתמש השני לא נוצר active purchase
        assertNull(
                purchaseRepository.findByUserID(secondUserId)
        );
    }

    @Test
    public void twoUsersSelectSameSittingTicket_onlyOneSucceeds() throws InterruptedException {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();

        Event event = event(eventId, companyId);

        event.getLayout().addArea(
                new org.example.DomainLayer.EventAggregate.SittingArea(areaId, 100f)
        );

        event.addTicket(
                new org.example.DomainLayer.EventAggregate.SittingTicket(
                        ticketId,
                        eventId,
                        areaId,
                        100f,
                        1,
                        1
                )
        );

        eventRepository.save(event);
    userRepository.add(new User(user1Id, "user1", "user1@mail.com", "pass", 20));
    userRepository.add(new User(user2Id, "user2", "user2@mail.com", "pass", 20));

        CountDownLatch startTogether = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        Runnable user1Task = () -> {
            try {
                startTogether.await();

                purchaseDomainService.selectSittingTickets(
                        eventId,
                        List.of(ticketId),
                        user1Id,
                        false
                );

                successCount.incrementAndGet();

            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        };

        Runnable user2Task = () -> {
            try {
                startTogether.await();

                purchaseDomainService.selectSittingTickets(
                        eventId,
                        List.of(ticketId),
                        user2Id,
                        false
                );

                successCount.incrementAndGet();

            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        };

        Thread t1 = new Thread(user1Task);
        Thread t2 = new Thread(user2Task);

        t1.start();
        t2.start();

        startTogether.countDown();

        t1.join();
        t2.join();

        assertEquals(1, successCount.get());
        assertEquals(1, failCount.get());

        ActivePurchase p1 = purchaseRepository.findByUserID(user1Id);
        ActivePurchase p2 = purchaseRepository.findByUserID(user2Id);

        int purchasesWithTicket = 0;

        if (p1 != null && p1.getTicketIDs().containsKey(ticketId)) {
            purchasesWithTicket++;
        }

        if (p2 != null && p2.getTicketIDs().containsKey(ticketId)) {
            purchasesWithTicket++;
        }

        assertEquals(1, purchasesWithTicket);

        assertEquals(
                org.example.DomainLayer.EventAggregate.TicketStatus.RESERVED,
                event.getTicket(ticketId).getStatus()
        );
    }

    private static class FakeHistoryRepository implements IHistoryRepository {

        private final List<PurchaseHistory> history = new ArrayList<>();

        @Override
        public void add(PurchaseHistory purchaseHistory) {
            history.add(purchaseHistory);
        }

        @Override
        public List<PurchaseHistory> getAll() {
            return new ArrayList<>(history);
        }

        @Override
        public List<PurchaseHistory> getByUserId(UUID userId) {
            return history.stream()
                    .filter(purchase -> purchase.getUserId().equals(userId))
                    .toList();
        }

        @Override
        public List<PurchaseHistory> getByEventId(UUID eventId) {
            return history.stream()
                    .filter(purchase -> purchase.getEventId().equals(eventId))
                    .toList();
        }
    }

    private static class FakeEventRepository implements IEventRepository {

        private final LinkedHashMap<UUID, Event> eventsById = new LinkedHashMap<>();

        @Override
        public Event getById(UUID eventId) {
            return eventsById.get(eventId);
        }

        @Override
        public List<Event> getAll() {
            return new ArrayList<>(eventsById.values());
        }

        @Override
        public void save(Event event) {
            eventsById.put(event.getEventId(), event);
        }

        @Override
        public void delete(UUID eventId) {
            eventsById.remove(eventId);
        }
    }

    private static class FakeCompanyRepository implements ICompanyRepository {

        private final LinkedHashMap<UUID, Company> companiesById = new LinkedHashMap<>();

        public void save(UUID companyId, Company company) {
            companiesById.put(companyId, company);
        }

        @Override
        public UUID createCompany(String founderUsername, String companyName) {
            return null;
        }

        @Override
        public Optional<Company> findByID(UUID companyId) {
            return Optional.ofNullable(companiesById.get(companyId));
        }

        @Override
        public void save(Company company) {
            companiesById.put(company.getId(), company);
        }
    }

    private static class FakeUserRepository implements IUserRepository {

        private final LinkedHashMap<UUID, User> usersById = new LinkedHashMap<>();

        @Override
        public void add(User user) {
            usersById.put(user.getId(), user);
        }

        @Override
        public Optional<User> getUser(UUID UID) {
            return Optional.ofNullable(usersById.get(UID));
        }

        @Override
        public boolean exists(UUID userId) {
            return usersById.containsKey(userId);
        }

        @Override
        public boolean existsByUsername(String username) {
            return usersById.values().stream().anyMatch(user -> user.getUsername().equals(username));
        }

        @Override
        public boolean isSystemAdmin(String username) {
            return false;
        }

        @Override
        public boolean existsByEmail(String email) {
            return false;
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return usersById.values().stream().filter(u -> u.getEmail().equals(email)).findFirst();
        }

        @Override
        public boolean existsAdmin(UUID adminId) {
            return false;
        }

        @Override
        public List<UUID> getCompaniesIdsByMember(String username) {
            return new ArrayList<>();
        }

        @Override
        public boolean isCompanyOwner(String username, UUID companyId) {
            Optional<User> u = findByEmail(username);
            return u.map(user -> user.isOwnerInCompany(companyId)).orElse(false);
        }

        @Override
        public boolean hasPermission(String username, UUID companyId, CompanyPermission permission, UUID eventId) {
            Optional<User> u = findByEmail(username);
            return u.map(user -> user.hasPremisions(companyId, permission, eventId)).orElse(false);
        }

        @Override
        public Map<UUID, User> getAllUsers() {
            return Map.of();
        }
    }



    private static class FakePurchaseRepository implements IPurchaseRepository {

        private final LinkedHashMap<UUID, ActivePurchase> purchasesById = new LinkedHashMap<>();

        @Override
        public ActivePurchase findByUserID(UUID userID) {
            return purchasesById.values().stream()
                    .filter(p -> p.getUserID().equals(userID))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public ActivePurchase findByID(UUID purchaseID) {
            return purchasesById.get(purchaseID);
        }

        @Override
        public void save(ActivePurchase activePurchase) {
            purchasesById.put(activePurchase.getActivePurchaseId(), activePurchase);
        }

        @Override
        public void deleteByID(UUID activePurchaseID) {
            purchasesById.remove(activePurchaseID);
        }
        @Override
        public List<ActivePurchase> findAll() {
            return new ArrayList<>(purchasesById.values());
        }

    }

    private static class FakeLotteryRepository implements ILotteryRepository {
        private final LinkedHashMap<UUID, PuchaseLottery> lotteriesById = new LinkedHashMap<>();
        private final LinkedHashMap<UUID, PuchaseLottery> lotteriesByEventId = new LinkedHashMap<>();

        @Override
        public void save(PuchaseLottery lottery) {
            lotteriesById.put(lottery.getLotteryId(), lottery);
            lotteriesByEventId.put(lottery.getEventId(), lottery);
        }

        @Override
        public PuchaseLottery findByID(UUID lotteryId) {
            return lotteriesById.get(lotteryId);
        }

        @Override
        public PuchaseLottery findByEventID(UUID eventId) {
            return lotteriesByEventId.get(eventId);
        }
    }
}