package org.example.ApplicationLayer;

import org.example.DomainLayer.*;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.*;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.User;
import org.example.InfrastructureLayer.Broadcaster;
import org.example.InfrastructureLayer.WebSocketNotificationSender;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class PurchaseServiceTest {

    private PurchaseDomainService purchaseDomainServiceMock;
    private PurchaseService purchaseService;
    private Broadcaster broadcaster;
    
    @Mock
    private QueueManager queueManagerMock;

    @Before
    public void setUp() {
        queueManagerMock = mock(QueueManager.class);
        purchaseDomainServiceMock = mock(PurchaseDomainService.class);
        broadcaster = new Broadcaster();
        INotifier notifier =new WebSocketNotificationSender(broadcaster);
        NotificationService notificationService =new NotificationService(notifier);
        EventListener eventListener =new EventListener(notificationService);
        EventPublisher eventPublisher =new EventPublisher();
        eventPublisher.subscribe(eventListener::handle);
        purchaseService = new PurchaseService(
                purchaseDomainServiceMock,
                eventPublisher,
                queueManagerMock
        );
    }

    /*
     * Admin purchase history filter tests
     */

    @Test
    public void getHistoryByFilter_whenFilterIsUser_returnsUserHistory() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByUser(userId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "user", userId);

        assertSame(expected, result);

        verify(purchaseDomainServiceMock).validateAdmin(adminId);
        verify(purchaseDomainServiceMock).getHistoryByUser(userId);
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenFilterIsEvent_returnsEventHistory() {
        UUID adminId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByEvent(eventId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "event", eventId);

        assertSame(expected, result);

        verify(purchaseDomainServiceMock).validateAdmin(adminId);
        verify(purchaseDomainServiceMock).getHistoryByEvent(eventId);
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenFilterIsCompany_returnsCompanyHistory() {
        UUID adminId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByCompany(companyId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "company", companyId);

        assertSame(expected, result);

        verify(purchaseDomainServiceMock).validateAdmin(adminId);
        verify(purchaseDomainServiceMock).getHistoryByCompany(companyId);
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenFilterIsAll_returnsAllHistory() {
        UUID adminId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(
                mock(PurchaseHistory.class),
                mock(PurchaseHistory.class)
        );

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getAllHistory()).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "all", null);

        assertSame(expected, result);

        verify(purchaseDomainServiceMock).validateAdmin(adminId);
        verify(purchaseDomainServiceMock).getAllHistory();
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
    }

    @Test
    public void getHistoryByFilter_whenUserHistoryIsEmpty_returnsEmptyList() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByUser(userId)).thenReturn(List.of());

        List<PurchaseHistory> result =
                purchaseService.getHistoryByFilter(adminId, "user", userId);

        assertTrue(result.isEmpty());

        verify(purchaseDomainServiceMock).validateAdmin(adminId);
        verify(purchaseDomainServiceMock).getHistoryByUser(userId);
    }

    /*
     * Admin purchase history
     */

    @Test
    public void getHistoryByFilter_whenAdminIdIsNull_throwsExceptionAndDoesNotFetchHistory() {
        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(null, "user", UUID.randomUUID())
        );

        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenAdminIsInvalid_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "user", UUID.randomUUID())
        );

        verify(purchaseDomainServiceMock).validateAdmin(adminId);
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenFilterTypeIsNull_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, null, UUID.randomUUID())
        );

        verifyNoInteractions(purchaseDomainServiceMock);
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenFilterTypeIsBlank_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, " ", UUID.randomUUID())
        );

        verifyNoInteractions(purchaseDomainServiceMock);
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenFilterTypeIsInvalid_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "invalid", UUID.randomUUID())
        );

        verifyNoInteractions(purchaseDomainServiceMock);
        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
        verify(purchaseDomainServiceMock, never()).getAllHistory();
    }

    @Test
    public void getHistoryByFilter_whenUserFilterIdIsNull_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "user", null)
        );

        verify(purchaseDomainServiceMock, never()).getHistoryByUser(any());
        verifyNoInteractions(purchaseDomainServiceMock);
    }

    @Test
    public void getHistoryByFilter_whenEventFilterIdIsNull_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "event", null)
        );

        verifyNoInteractions(purchaseDomainServiceMock);
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
    }

    @Test
    public void getHistoryByFilter_whenCompanyFilterIdIsNull_throwsExceptionAndDoesNotFetchHistory() {
        UUID adminId = UUID.randomUUID();

        when(purchaseDomainServiceMock.validateAdmin(adminId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getHistoryByFilter(adminId, "company", null)
        );

        verifyNoInteractions(purchaseDomainServiceMock);
        verify(purchaseDomainServiceMock, never()).getHistoryByCompany(any());
    }

    /*
     * Member purchase history tests
     */

    @Test
    public void getPurchaseHistoryForMember_whenMemberIsValidAndLoggedIn_returnsMemberHistory() {
        UUID memberId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMember(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMemberLoggedIn(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.getPurchaseHistoryForMember(memberId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getPurchaseHistoryForMember(memberId);

        assertSame(expected, result);

        verify(purchaseDomainServiceMock).memberExists(memberId);
        verify(purchaseDomainServiceMock).isMember(memberId);
        verify(purchaseDomainServiceMock).isMemberLoggedIn(memberId);
        verify(purchaseDomainServiceMock).getPurchaseHistoryForMember(memberId);
    }

    @Test
    public void getPurchaseHistoryForMember_whenHistoryIsEmpty_returnsEmptyList() {
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
    public void getPurchaseHistoryForMember_whenMemberIdIsNull_throwsExceptionAndDoesNotTouchDomain() {
        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(null)
        );

        verifyNoInteractions(purchaseDomainServiceMock);
    }

    @Test
    public void getPurchaseHistoryForMember_whenMemberDoesNotExist_throwsExceptionAndDoesNotFetchHistory() {
        UUID memberId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(memberId)
        );

        verify(purchaseDomainServiceMock).memberExists(memberId);
        verify(purchaseDomainServiceMock, never()).isMember(any());
        verify(purchaseDomainServiceMock, never()).isMemberLoggedIn(any());
        verify(purchaseDomainServiceMock, never()).getPurchaseHistoryForMember(any());
    }

    @Test
    public void getPurchaseHistoryForMember_whenUserIsNotMember_throwsExceptionAndDoesNotFetchHistory() {
        UUID userId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(userId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMember(userId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(userId)
        );

        verify(purchaseDomainServiceMock).memberExists(userId);
        verify(purchaseDomainServiceMock).isMember(userId);
        verify(purchaseDomainServiceMock, never()).isMemberLoggedIn(any());
        verify(purchaseDomainServiceMock, never()).getPurchaseHistoryForMember(any());
    }

    @Test
    public void getPurchaseHistoryForMember_whenMemberIsNotLoggedIn_throwsExceptionAndDoesNotFetchHistory() {
        UUID memberId = UUID.randomUUID();

        when(purchaseDomainServiceMock.memberExists(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMember(memberId)).thenReturn(true);
        when(purchaseDomainServiceMock.isMemberLoggedIn(memberId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getPurchaseHistoryForMember(memberId)
        );

        verify(purchaseDomainServiceMock).memberExists(memberId);
        verify(purchaseDomainServiceMock).isMember(memberId);
        verify(purchaseDomainServiceMock).isMemberLoggedIn(memberId);
        verify(purchaseDomainServiceMock, never()).getPurchaseHistoryForMember(any());
    }

    /*
     * Owner event purchase history tests
     */

    @Test
    public void getEventPurchaseHistoryForOwner_whenOwnerOwnsEvent_returnsEventHistory() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        List<PurchaseHistory> expected = List.of(mock(PurchaseHistory.class));

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.isCompanyOwnerOfEvent(ownerName, eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.getHistoryByEvent(eventId)).thenReturn(expected);

        List<PurchaseHistory> result =
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId);

        assertSame(expected, result);

        verify(purchaseDomainServiceMock).eventExists(eventId);
        verify(purchaseDomainServiceMock).isCompanyOwnerOfEvent(ownerName, eventId);
        verify(purchaseDomainServiceMock).getHistoryByEvent(eventId);
    }

    @Test
    public void getEventPurchaseHistoryForOwner_whenEventDoesNotExist_throwsExceptionAndDoesNotFetchHistory() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId)
        );

        verify(purchaseDomainServiceMock).eventExists(eventId);
        verify(purchaseDomainServiceMock, never()).isCompanyOwnerOfEvent(anyString(), any());
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
    }

    @Test
    public void getEventPurchaseHistoryForOwner_whenOwnerDoesNotOwnEvent_throwsExceptionAndDoesNotFetchHistory() {
        String ownerName = "owner";
        UUID eventId = UUID.randomUUID();

        when(purchaseDomainServiceMock.eventExists(eventId)).thenReturn(true);
        when(purchaseDomainServiceMock.isCompanyOwnerOfEvent(ownerName, eventId)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                purchaseService.getEventPurchaseHistoryForOwner(ownerName, eventId)
        );

        verify(purchaseDomainServiceMock).eventExists(eventId);
        verify(purchaseDomainServiceMock).isCompanyOwnerOfEvent(ownerName, eventId);
        verify(purchaseDomainServiceMock, never()).getHistoryByEvent(any());
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
        Broadcaster broadcaster;
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
        setup.broadcaster = new Broadcaster();
        INotifier notifier =new WebSocketNotificationSender(setup.broadcaster);
        NotificationService notificationService =new NotificationService(notifier);
        EventListener eventListener =new EventListener(notificationService);
        EventPublisher eventPublisher =new EventPublisher();
        eventPublisher.subscribe(eventListener::handle);

        setup.purchaseService =
                new PurchaseService(
                        setup.purchaseDomainService,
                        eventPublisher,
                        setup.queueManager
                );
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

    @Test
    public void selectSittingTickets_whenEventIsLottery_throwsExceptionAndDoesNotTouchQueue() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        List<UUID> ticketIds = List.of(UUID.randomUUID());

        // מדמים מצב שבו ה-Domain Service מזהה שהאירוע הוא הגרלה
        when(purchaseDomainServiceMock.isLotteryEvent(eventId)).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
                purchaseService.selectSittingTickets(eventId, ticketIds, userId, false)
        );

        // ולידציה של Fail-Fast: מוודאים שלא ניגשנו לתור ולא ניסינו לשריין כרטיסים בדומיין
        verify(queueManagerMock, never()).requestSelectionAccess(any(), any());
        verify(purchaseDomainServiceMock, never()).selectSittingTickets(any(), any(), any(), anyBoolean());
    }

    @Test
    public void selectSittingTickets_withActiveLotteryInSystem_failsPurchase() {
        // Arrange
        TestSetup setup = createSetup();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        // הגדרת הגרלה במערכת לאירוע הזה
        PuchaseLottery lottery = new PuchaseLottery(
                UUID.randomUUID(),
                eventId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );
        setup.inMemoryLotteryRepository.save(lottery);

        // Act & Assert
        assertThrows(IllegalStateException.class, () ->
                setup.purchaseService.selectSittingTickets(eventId, List.of(UUID.randomUUID()), userId, false)
        );

        // וידוא שלא נוצר ActivePurchase במאגר
        assertNull(setup.inMemoryPurchaseRepository.findByUserID(userId));
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
    private static class InMemoryLotteryRepository implements ILotteryRepository {
        private final Map<UUID, PuchaseLottery> lotteriesByEvent = new HashMap<>();

        @Override
        public void save(PuchaseLottery lottery) {
            lotteriesByEvent.put(lottery.getEventId(), lottery);
        }

        @Override
        public PuchaseLottery findByID(UUID lotteryId) { return null; }

        @Override
        public PuchaseLottery findByEventID(UUID eventId) {
            return lotteriesByEvent.get(eventId);
        }
    }

    @Test
    public void completePurchase_success_publishesNotificationToUser() {
        TestSetup setup = createSetup();

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID areaID = UUID.randomUUID();

        Event event = new Event(eventId, companyId, LocalDateTime.now(), "loc", "artist", "type", EventStatus.ACTIVE);
        event.getLayout().addArea(new StandingArea(areaID, 100f));
        event.addTicket(new StandingTicket(ticketId, eventId, areaID, 100f));

        setup.inMemoryEventRepository.save(event);
        setup.innMemoryUserRepository.add(new User(userId, "user", "email", "pass", 20));

        setup.purchaseDomainService.setPaymentGateway(new IPaymentGateway() {
            @Override
            public boolean pay(UUID userID, float amount, PaymentDetails paymentDetails) {
                return true;
            }
        });

        setup.purchaseDomainService.setTicketingGateway(new ITicketingGateway() {
            @Override
            public void issueTickets(UUID userId, UUID eventId, Set<UUID> ticketIds) {
            }
        });

        final List<String> receivedMessages = new ArrayList<>();

        setup.broadcaster.register(userId.toString(), message -> {
            receivedMessages.add(message);
        });

        setup.purchaseService.selectStandingTickets(eventId, 1, areaID, userId, false);

        ActivePurchase activePurchase = setup.inMemoryPurchaseRepository.findByUserID(userId);

        setup.purchaseService.completePurchase(activePurchase.getActivePurchaseId(), new PaymentDetails(), null);

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(1, receivedMessages.size());
        assertEquals("Your purchase was completed successfully.", receivedMessages.get(0));
    }


}
