package org.example.ApplicationLayer;

import org.example.DomainLayer.*;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.ActivePurchaseAggregate.IPaymentGateway;
import org.example.DomainLayer.ActivePurchaseAggregate.ITicketingGateway;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.*;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.User;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PurchaseServiceTest {
    private PurchaseDomainService purchaseDomainServiceMock;
    private PurchaseService purchaseService;
    
    @Mock
    private QueueManager queueManagerMock;

    @Before
    public void setUp() {
        queueManagerMock = mock(QueueManager.class);
        purchaseDomainServiceMock = mock(PurchaseDomainService.class);
        purchaseService = new PurchaseService(purchaseDomainServiceMock, queueManagerMock);
    }

    /* Tests for viewing purchase history by filter */

    @Test
    public void testViewPurchaseHistoryByFilter_UserSuccess() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));
        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByUser(userId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "user", userId);

        assertEquals(expected, result);
    }

    @Test
    public void testViewPurchaseHistoryByFilter_EventSuccess() {
        UUID adminId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));
        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByEvent(eventId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "event", eventId);

        assertEquals(expected, result);
    }

    @Test
    public void testViewPurchaseHistoryByFilter_CompanySuccess() {
        UUID adminId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));
        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByCompany(companyId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "company", companyId);

        assertEquals(expected, result);
    }

    @Test
    public void testViewPurchaseHistoryByFilter_AllSuccess() {
        UUID adminId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));
        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getAllHistory()).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "all", null);

        assertEquals(expected, result);
    }

    @Test
    public void testViewPurchaseHistoryByFilter_NullFilterType() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, null, UUID.randomUUID())
        );
    }

    @Test
    public void testViewPurchaseHistoryByFilter_BlankFilterType() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, " ", UUID.randomUUID())
        );
    }

    @Test
    public void testViewPurchaseHistoryByFilter_UserMissingFilterId() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "user", null)
        );
    }

    @Test
    public void testViewPurchaseHistoryByFilter_InvalidFilterType() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "invalid", UUID.randomUUID())
        );
    }

    @Test
    public void testViewPurchaseHistoryByFilter_UnauthorizedUser() {
        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(null, "user", UUID.randomUUID())
        );
    }

    @Test
    public void testViewPurchaseHistoryByFilter_EmptyResult() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByUser(userId)).thenReturn(List.of());

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "user", userId);

        assertTrue(result.isEmpty());
    }

    /* Tests for viewing purchase history for member */

    @Test
    public void testSuccessfulViewPurchaseHistory() {
        UUID memberId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMember(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMemberLoggedIn(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.getPurchaseHistoryForMember(memberId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getPurchaseHistoryForMember(memberId);

        assertEquals(expected, result);
    }
    @Test
    public void testPurchaseHistoryUserNotFound() {
        UUID memberId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(memberId)
        );
    }

    @Test
    public void testEmptyPurchaseHistory() {
        UUID memberId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMember(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMemberLoggedIn(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.getPurchaseHistoryForMember(memberId)).thenReturn(List.of());

        List<PurchaseHistory> result =
                purchaseService.getPurchaseHistoryForMember(memberId);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testPurchaseHistoryUserNotLoggedIn() {
        UUID memberId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMemberLoggedIn(memberId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(memberId)
        );
    }

    @Test
    public void testPurchaseHistoryMemberIdIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(null)
        );
    }

    @Test
    public void testPurchaseHistory_UserIsNotMember() {
        UUID userId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(userId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMember(userId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(userId)
        );
    }


    /* Tests for viewing event purchase history for owner */
    @Test
    public void testGetEventPurchaseHistoryForOwner_Success() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.isCompanyOwnerOfEvent(ownerName, eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByEvent(eventId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId);

        assertEquals(expected, result);
    }

    @Test
    public void testGetEventPurchaseHistoryForOwner_EventDoesNotExist() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId)
        );
    }

    @Test
    public void testGetEventPurchaseHistoryForOwner_UnauthorizedOwner() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.isCompanyOwnerOfEvent(ownerName, eventId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId)
        );
    }

    @Test
    public void testGetEventPurchaseHistoryForOwner_CancelledEventStillReturnsHistory() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.isCompanyOwnerOfEvent(ownerName, eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByEvent(eventId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId);

        assertEquals(expected, result);
    }


    private static class TestSetup
    {
        InMemoryUserRepository innMemoryUserRepository;
        InMemoryCompanyRepository inMemoryCompanyRepository;
        InMemoryEventRepository inMemoryEventRepository;
        InMemoryHistoryRepository inMemoryHistoryRepository;
        InMemoryPurchaseRepository inMemoryPurchaseRepository;
        InMemoryLotteryRepository inMemoryLotteryRepository;


        QueueManager queueManager;
        PurchaseDomainService purchaseDomainService;
        PurchaseService purchaseService;
    }

    private TestSetup createSetup()
    {
        TestSetup setup = new TestSetup();
        setup.innMemoryUserRepository = new InMemoryUserRepository();
        setup.inMemoryCompanyRepository = new InMemoryCompanyRepository();
        setup.inMemoryEventRepository = new InMemoryEventRepository();
        setup.inMemoryHistoryRepository = new InMemoryHistoryRepository();
        setup.inMemoryPurchaseRepository = new InMemoryPurchaseRepository();
        setup.inMemoryLotteryRepository = new InMemoryLotteryRepository();

        setup.queueManager = new QueueManager();
        setup.purchaseDomainService = new PurchaseDomainService(setup.inMemoryHistoryRepository, setup.inMemoryEventRepository, setup.inMemoryPurchaseRepository, setup.inMemoryCompanyRepository, setup.innMemoryUserRepository, setup.inMemoryLotteryRepository);
        setup.purchaseService = new PurchaseService(setup.purchaseDomainService, setup.queueManager);

        return setup;
    }

    //בדיקות מקביליות
    @Test
    public void selectSittingTickets_success_createPurchase()
    {
        //כל הסטאפ שצריך לבדיקה הזאת
        TestSetup setup = createSetup();

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID areaID = UUID.randomUUID();


        SittingArea sittingArea = new SittingArea(areaID, 100f);

        List<UUID> ticketIDs = new ArrayList<>();
        ticketIDs.add(ticketId);

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "dssdsd", "sdsdsd", "sdsd", EventStatus.ACTIVE);
        event.getLayout().addArea(sittingArea);

        SittingTicket ticket = new SittingTicket(ticketId, eventId, areaID, 100f, 1, 1);
        event.addTicket(ticket);

        setup.inMemoryEventRepository.save(event);

        User user = new User(userId, "hello", "hello", "hello", 20);
        setup.innMemoryUserRepository.add(user);


        setup.queueManager.requestSelectionAccess(userId, eventId);
        setup.purchaseService.selectSittingTickets(eventId, ticketIDs, userId, false);

        //עכשיו הבדיקות עצמן
        ActivePurchase activePurchase = setup.inMemoryPurchaseRepository.findByUserID(userId);
        assertNotNull(activePurchase);
        assertEquals(event.getEventId(), activePurchase.getEventID());
        assertTrue(activePurchase.getTicketIDs().containsKey(ticketId));
        assertEquals(TicketStatus.RESERVED, event.getTicket(ticketId).getStatus());

    }
    @Test
    public void twoUsersSelectSameSittingTicket_onlyOneSucceeds() throws InterruptedException {
        TestSetup setup = createSetup();

        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "loc", "artist", "type", EventStatus.ACTIVE);

        SittingArea area = new SittingArea(areaId, 100f);
        event.getLayout().addArea(area);

        SittingTicket ticket = new SittingTicket(ticketId, eventId, areaId, 100f, 1, 1);
        event.addTicket(ticket);

        setup.inMemoryEventRepository.save(event);

        setup.innMemoryUserRepository.add(new User(user1Id, "user1", "u1@mail.com", "pass", 20));
        setup.innMemoryUserRepository.add(new User(user2Id, "user2", "u2@mail.com", "pass", 20));

        setup.queueManager.requestSelectionAccess(user1Id, eventId);
        setup.queueManager.requestSelectionAccess(user2Id, eventId);

        CountDownLatch startTogether = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        Runnable user1Task = () -> {
            try {
                startTogether.await();
                setup.purchaseService.selectSittingTickets(eventId, List.of(ticketId), user1Id, false);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
            }
        };

        Runnable user2Task = () -> {
            try {
                startTogether.await();
                setup.purchaseService.selectSittingTickets(eventId, List.of(ticketId), user2Id, false);
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

        ActivePurchase p1 = setup.inMemoryPurchaseRepository.findByUserID(user1Id);
        ActivePurchase p2 = setup.inMemoryPurchaseRepository.findByUserID(user2Id);

        int purchasesWithTicket = 0;

        if (p1 != null && p1.getTicketIDs().containsKey(ticketId)) {
            purchasesWithTicket++;
        }

        if (p2 != null && p2.getTicketIDs().containsKey(ticketId)) {
            purchasesWithTicket++;
        }

        assertEquals(1, purchasesWithTicket);
        assertEquals(TicketStatus.RESERVED, event.getTicket(ticketId).getStatus());
    }

    @Test
    public void selectSittingTickets_success_consumesQueueAccess() {
        TestSetup setup = createSetup();

        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "loc", "artist", "type", EventStatus.ACTIVE);
        SittingArea area = new SittingArea(areaId, 100f);
        event.getLayout().addArea(area);
        event.addTicket(new SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));

        setup.inMemoryEventRepository.save(event);
        setup.innMemoryUserRepository.add(new User(userId, "u", "u", "u", 20));

        //משתמש ביקש גישה להתחלה של הבחירה
        setup.queueManager.requestSelectionAccess(userId, eventId);

        //נוודא שיש לו גישה
        assertTrue(setup.queueManager.hasSelectAccess(userId, eventId));

        //הוא בחר כרטיס ולכן סיים את הזכות שלו לבחור
        setup.purchaseService.selectSittingTickets(eventId, List.of(ticketId), userId, false);

        assertFalse(setup.queueManager.hasSelectAccess(userId, eventId));
    }

    @Test
    public void selectSittingTickets_failure_doesNotConsumeQueueAccess() {
        TestSetup setup = createSetup();

        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "loc", "artist", "type", EventStatus.ACTIVE);
        SittingArea area = new SittingArea(areaId, 100f);
        event.getLayout().addArea(area);
        event.addTicket(new SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));

        setup.inMemoryEventRepository.save(event);
        setup.innMemoryUserRepository.add(new User(userId, "u", "u", "u", 20));

        //המשתמש מבקש הרשאה לבחור כרטיסים
        setup.queueManager.requestSelectionAccess(userId, eventId);

        //אחד הכרטיסים שוריין
        event.getTicket(ticketId).reserve(); // גורם לכישלון

        //עכשיו כשהוא יבחר אותו נצפה לשגיאה
        assertThrows(IllegalStateException.class, () ->
                setup.purchaseService.selectSittingTickets(eventId, List.of(ticketId), userId, false)
        );

        //אבל עדיין יש לו הרשאה
        assertTrue(setup.queueManager.hasSelectAccess(userId, eventId));
    }

    @Test
    public void selectSittingTickets_whenQueueFull_throwsAndDoesNotCreatePurchase() {
        TestSetup setup = createSetup();

        //בוא נניח שרק אחד יכול לבחור במקביל
        setup.queueManager.setMaxConcurrentSelectors(1);

        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "loc", "artist", "type", EventStatus.ACTIVE);
        event.getLayout().addArea(new SittingArea(areaId, 100f));
        event.addTicket(new SittingTicket(ticketId, eventId, areaId, 100f, 1, 1));

        setup.inMemoryEventRepository.save(event);

        setup.innMemoryUserRepository.add(new User(user1, "u1", "u1", "u1", 20));
        setup.innMemoryUserRepository.add(new User(user2, "u2", "u2", "u2", 20));

        //המשתמש הראשון נכנס ישר
        setup.queueManager.requestSelectionAccess(user1, eventId);

        //המשתמש השני נכנס לתור ולא מצליח עדיין להיכנס, אז תיזרק שגיאה של המתנה עם מיקום בתור
        assertThrows(IllegalStateException.class, () ->
                setup.purchaseService.selectSittingTickets(eventId, List.of(ticketId), user2, false)
        );

        //זה גם אומר שלמשתמש השני מן הסתם לא נוצר active purchase
        assertNull(setup.inMemoryPurchaseRepository.findByUserID(user2));

        //הכרטיס אמור להיות עדיין פנוי
        assertEquals(TicketStatus.AVAILABLE, event.getTicket(ticketId).getStatus());
        //והמיקום של המשתמש השני הוא ב-1
        assertEquals(1, setup.queueManager.getPositionInQueue(user2, eventId));
    }


    //בדיקות רגילות
    @Test
    public void selectStandingTickets_success()
    {
        TestSetup setup = createSetup();

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID areaID = UUID.randomUUID();

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "dssdsd", "sdsdsd", "sdsd", EventStatus.ACTIVE);
        event.getLayout().addArea(new StandingArea(areaID, 100f));
        event.addTicket(new StandingTicket(ticketId, eventId, areaID, 100f));

        setup.inMemoryEventRepository.save(event);

        User user = new User(userId, "hello", "hello", "hello", 20);
        setup.innMemoryUserRepository.add(user);

        setup.purchaseService.selectStandingTickets(eventId, 1, areaID, userId, false);

        ActivePurchase activePurchase = setup.inMemoryPurchaseRepository.findByUserID(userId);
        assertNotNull(activePurchase);
        assertEquals(event.getEventId(), activePurchase.getEventID());
        assertTrue(activePurchase.getTicketIDs().containsKey(ticketId));
        assertEquals(TicketStatus.RESERVED, event.getTicket(ticketId).getStatus());
    }
    @Test
    public void selectStandingTickets_failure()
    {
        TestSetup setup = createSetup();

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID areaID = UUID.randomUUID();

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "dssdsd", "sdsdsd", "sdsd", EventStatus.ACTIVE);
        event.getLayout().addArea(new StandingArea(areaID, 100f));
        event.addTicket(new StandingTicket(ticketId, eventId, areaID, 100f));

        setup.inMemoryEventRepository.save(event);

        User user = new User(userId, "hello", "hello", "hello", 20);
        setup.innMemoryUserRepository.add(user);

        //יש רק ticket אחד ב-event ואנחנו מנסים לקנות 2
        assertThrows(IllegalStateException.class, () -> setup.purchaseService.selectStandingTickets(eventId, 2, areaID, userId, false));
        ActivePurchase activePurchase = setup.inMemoryPurchaseRepository.findByUserID(userId);
        assertNull(activePurchase);
    }
    @Test
    public void completePurchase_success()
    {
        TestSetup setup = createSetup();

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID areaID = UUID.randomUUID();

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "dssdsd", "sdsdsd", "sdsd", EventStatus.ACTIVE);
        event.getLayout().addArea(new StandingArea(areaID, 100f));
        event.addTicket(new StandingTicket(ticketId, eventId, areaID, 100f));

        IPaymentGateway paymentGateway = new IPaymentGateway() {
            @Override
            public boolean pay(UUID userID, float amount, PaymentDetails paymentDetails) {
                return true;
            }
        };
        ITicketingGateway ticketingGateway = new ITicketingGateway() {
            @Override
            public void issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds) {

            }
        };

        setup.purchaseDomainService.setPaymentGateway(paymentGateway);
        setup.purchaseDomainService.setTicketingGateway(ticketingGateway);



        setup.inMemoryEventRepository.save(event);

        User user = new User(userId, "hello", "hello", "hello", 20);
        setup.innMemoryUserRepository.add(user);

        setup.purchaseService.selectStandingTickets(eventId, 1, areaID, userId, false);
        ActivePurchase activePurchase = setup.inMemoryPurchaseRepository.findByUserID(userId);

        setup.purchaseService.completePurchase(activePurchase.getActivePurchaseId(), new PaymentDetails(), null);

        assertNull(setup.inMemoryPurchaseRepository.findByID(activePurchase.getActivePurchaseId()));

        Ticket ticket = event.getTicket(ticketId);
        assertEquals(TicketStatus.SOLD, ticket.getStatus());

    }
    @Test
    public void completePurchase_failure()
    {
        TestSetup setup = createSetup();

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID areaID = UUID.randomUUID();

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "dssdsd", "sdsdsd", "sdsd", EventStatus.ACTIVE);
        event.getLayout().addArea(new StandingArea(areaID, 100f));
        event.addTicket(new StandingTicket(ticketId, eventId, areaID, 100f));

        IPaymentGateway paymentGateway = new IPaymentGateway() {
            @Override
            public boolean pay(UUID userID, float amount, PaymentDetails paymentDetails) {
                return false;
            }
        };
        ITicketingGateway ticketingGateway = new ITicketingGateway() {
            @Override
            public void issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds) {

            }
        };

        setup.purchaseDomainService.setPaymentGateway(paymentGateway);
        setup.purchaseDomainService.setTicketingGateway(ticketingGateway);

        setup.inMemoryEventRepository.save(event);

        User user = new User(userId, "hello", "hello", "hello", 20);
        setup.innMemoryUserRepository.add(user);

        setup.purchaseService.selectStandingTickets(eventId, 1, areaID, userId, false);
        ActivePurchase activePurchase = setup.inMemoryPurchaseRepository.findByUserID(userId);

        assertThrows(IllegalStateException.class, () -> setup.purchaseService.completePurchase(activePurchase.getActivePurchaseId(), new PaymentDetails(), null));
    }
    @Test
    public void cancelPurchase_success()
    {
        TestSetup setup = createSetup();

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID areaID = UUID.randomUUID();

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "loc", "artist", "type", EventStatus.ACTIVE);
        event.getLayout().addArea(new SittingArea(areaID, 100f));
        event.addTicket(new SittingTicket(ticketId, eventId, areaID, 100f, 1, 1));

        setup.inMemoryEventRepository.save(event);
        setup.innMemoryUserRepository.add(new User(userId, "user", "email", "pass", 20));

        setup.queueManager.requestSelectionAccess(userId, eventId);

        setup.purchaseService.selectSittingTickets(eventId, List.of(ticketId), userId, false);

        ActivePurchase activePurchase = setup.inMemoryPurchaseRepository.findByUserID(userId);
        assertNotNull(activePurchase);
        assertEquals(TicketStatus.RESERVED, event.getTicket(ticketId).getStatus());

        setup.purchaseService.cancelActivePurchase(activePurchase.getActivePurchaseId());

        assertNull(setup.inMemoryPurchaseRepository.findByID(activePurchase.getActivePurchaseId()));
        assertEquals(TicketStatus.AVAILABLE, event.getTicket(ticketId).getStatus());
    }
    @Test
    public void cancelPurchase_failure()
    {
        TestSetup setup = createSetup();

        UUID nonExistingId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () ->
                setup.purchaseService.cancelActivePurchase(nonExistingId)
        );
    }
    @Test
    public void updateActivePurchase_success()
    {
        TestSetup setup = createSetup();

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaID = UUID.randomUUID();

        UUID oldTicketId = UUID.randomUUID();
        UUID newTicketId = UUID.randomUUID();

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "loc", "artist", "type", EventStatus.ACTIVE);
        event.getLayout().addArea(new SittingArea(areaID, 100f));
        event.addTicket(new SittingTicket(oldTicketId, eventId, areaID, 100f, 1, 1));
        event.addTicket(new SittingTicket(newTicketId, eventId, areaID, 100f, 2, 1));

        setup.inMemoryEventRepository.save(event);
        setup.innMemoryUserRepository.add(new User(userId, "user", "email", "pass", 20));

        setup.queueManager.requestSelectionAccess(userId, eventId);
        setup.purchaseService.selectSittingTickets(eventId, List.of(oldTicketId), userId, false);

        ActivePurchase purchase = setup.inMemoryPurchaseRepository.findByUserID(userId);

        setup.purchaseService.updateActivePurchaseSittingTickets(
                purchase.getActivePurchaseId(),
                List.of(newTicketId)
        );

        ActivePurchase updated = setup.inMemoryPurchaseRepository.findByID(purchase.getActivePurchaseId());

        assertFalse(updated.getTicketIDs().containsKey(oldTicketId));
        assertTrue(updated.getTicketIDs().containsKey(newTicketId));

        assertEquals(TicketStatus.AVAILABLE, event.getTicket(oldTicketId).getStatus());
        assertEquals(TicketStatus.RESERVED, event.getTicket(newTicketId).getStatus());
    }
    @Test
    public void updateActivePurchase_failure()
    {
        TestSetup setup = createSetup();

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID areaID = UUID.randomUUID();

        UUID oldTicketId = UUID.randomUUID();
        UUID newTicketId = UUID.randomUUID();

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "loc", "artist", "type", EventStatus.ACTIVE);
        event.getLayout().addArea(new SittingArea(areaID, 100f));
        event.addTicket(new SittingTicket(oldTicketId, eventId, areaID, 100f, 1, 1));
        event.addTicket(new SittingTicket(newTicketId, eventId, areaID, 100f, 2, 1));

        setup.inMemoryEventRepository.save(event);
        setup.innMemoryUserRepository.add(new User(userId, "user", "email", "pass", 20));

        setup.queueManager.requestSelectionAccess(userId, eventId);
        setup.purchaseService.selectSittingTickets(eventId, List.of(oldTicketId), userId, false);

        ActivePurchase purchase = setup.inMemoryPurchaseRepository.findByUserID(userId);

        // גורמים לכרטיס החדש להיות תפוס
        event.getTicket(newTicketId).reserve();

        assertThrows(IllegalStateException.class, () ->
                setup.purchaseService.updateActivePurchaseSittingTickets(
                        purchase.getActivePurchaseId(),
                        List.of(newTicketId)
                )
        );

        ActivePurchase unchanged = setup.inMemoryPurchaseRepository.findByID(purchase.getActivePurchaseId());

        assertTrue(unchanged.getTicketIDs().containsKey(oldTicketId));
        assertFalse(unchanged.getTicketIDs().containsKey(newTicketId));

        assertEquals(TicketStatus.RESERVED, event.getTicket(oldTicketId).getStatus());
        assertEquals(TicketStatus.RESERVED, event.getTicket(newTicketId).getStatus());
    }

    private static class InMemoryPurchaseRepository implements IPurchaseRepository
    {
        Map<UUID, ActivePurchase> purchasesByID = new LinkedHashMap<>();

        @Override
        public ActivePurchase findByUserID(UUID userID) {
            for (ActivePurchase purchase : purchasesByID.values()) {
                if (purchase.getUserID().equals(userID)) {
                    return purchase;
                }
            }
            return null;
        }

        @Override
        public ActivePurchase findByID(UUID purchaseID) {
            return purchasesByID.get(purchaseID);
        }

        @Override
        public void save(ActivePurchase activePurchase) {
            purchasesByID.put(activePurchase.getActivePurchaseId(), activePurchase);
        }

        @Override
        public void deleteByID(UUID activePurchaseID) {
            purchasesByID.remove(activePurchaseID);
        }
    }
    private static class InMemoryEventRepository implements IEventRepository
    {
        Map<UUID, Event> eventsByID = new LinkedHashMap<>();

        @Override
        public Event getById(UUID eventId) {
            return eventsByID.get(eventId);
        }

        @Override
        public List<Event> getAll() {
            return eventsByID.values().stream().toList();
        }

        @Override
        public void save(Event event) {
            eventsByID.put(event.getEventId(), event);
        }

        @Override
        public void delete(UUID eventId) {
            eventsByID.remove(eventId);
        }
    }
    private static class InMemoryUserRepository implements IUserRepository
    {
        private final Map<UUID, User> usersByID = new LinkedHashMap<>();
        @Override
        public void add(User user) {
            usersByID.put(user.getId(), user);
        }

        @Override
        public Optional<User> getUser(UUID UID) {
            return Optional.of(usersByID.get(UID));
        }

        @Override
        public boolean exists(UUID userId) {
            return usersByID.containsKey(userId);
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
            return Optional.empty();
        }

        @Override
        public boolean existsAdmin(UUID adminId) {
            return false;
        }
    }
    private static class InMemoryCompanyRepository implements ICompanyRepository
    {

        @Override
        public UUID createCompany(String founderUsername, String companyName) {
            return null;
        }

        @Override
        public Optional<Company> findByID(UUID companyId) {
            return Optional.empty();
        }

        @Override
        public boolean isOwner(String username, UUID companyId) {
            return false;
        }

        @Override
        public void save(Company company) {

        }

        @Override
        public List<Company> getCompaniesByMember(String username) {
            return List.of();
        }
    }
    public static class InMemoryHistoryRepository implements IHistoryRepository
    {

        @Override
        public void add(PurchaseHistory purchaseHistory) {

        }

        @Override
        public List<PurchaseHistory> getAll() {
            return List.of();
        }

        @Override
        public List<PurchaseHistory> getByUserId(UUID userId) {
            return List.of();
        }

        @Override
        public List<PurchaseHistory> getByEventId(UUID eventId) {
            return List.of();
        }
    }
    public static class InMemoryLotteryRepository implements ILotteryRepository
    {

        @Override
        public void save(PuchaseLottery lottery) {

        }

        @Override
        public PuchaseLottery findByID(UUID lotteryId) {
            return null;
        }

        @Override
        public PuchaseLottery findByEventID(UUID eventId) {
            return null;
        }
    }


}
