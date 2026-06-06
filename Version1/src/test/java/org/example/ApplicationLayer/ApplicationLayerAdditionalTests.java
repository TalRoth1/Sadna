package org.example.ApplicationLayer;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.example.ApplicationLayer.dto.PurchaseDTOs.SelectionAccessDTO;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.EventAggregate.SittingArea;
import org.example.DomainLayer.EventAggregate.SittingTicket;
import org.example.DomainLayer.EventAggregate.StandingArea;
import org.example.DomainLayer.EventAggregate.StandingTicket;
import org.example.DomainLayer.Events.IDomainEvent;
import org.example.DomainLayer.Events.LotteryWonEvent;
import org.example.DomainLayer.Events.PurchaseCompletedEvent;
import org.example.DomainLayer.IEventRepository;
import org.example.DomainLayer.ILotteryRepository;
import org.example.DomainLayer.INotificationRepository;
import org.example.DomainLayer.IPurchaseRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.RolesDomainService;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.InfrastructureLayer.InMemoryKeyedLock;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.UserAggregate.User;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class ApplicationLayerAdditionalTests {

    private static final String JWT_SECRET =
            "this-is-a-very-long-test-secret-key-for-hmac-sha256-1234567890";

    private INotifier notifier;
    private INotificationRepository notificationRepository;
    private NotificationService notificationService;

    private EventManagementDomainService eventDomainService;
    private EventService eventService;

    private RolesDomainService rolesDomainService;
    private PurchaseDomainService purchaseDomainService;
    private CompanyService companyService;

    private EventPublisher eventPublisher;
    private QueueManager queueManager;
    private PurchaseService purchaseService;

    @Before
    public void setUp() {
        notifier = mock(INotifier.class);
        notificationRepository = mock(INotificationRepository.class);
        notificationService = new NotificationService(notifier, notificationRepository);

        eventDomainService = mock(EventManagementDomainService.class);
        eventService = new EventService(eventDomainService, notifier);

        rolesDomainService = mock(RolesDomainService.class);
        purchaseDomainService = mock(PurchaseDomainService.class);
        companyService = new CompanyService(
                rolesDomainService,
                purchaseDomainService,
                notifier,
                eventService
        );

        eventPublisher = mock(EventPublisher.class);
        queueManager = mock(QueueManager.class);
        purchaseService = new PurchaseService(
                purchaseDomainService,
                eventPublisher,
                queueManager,
                notifier
        );
    }

    // ================================================================
    // JwtService + MintedToken
    // ================================================================

    /*
    @Disabled
    @Test
    public void jwtService_mintParseExtractAndValidate_validToken() {
        ITokenBlacklist blacklist = mock(ITokenBlacklist.class);
        JwtService jwtService = new JwtService(JWT_SECRET, 60_000, blacklist);

        UUID userId = UUID.randomUUID();

        JwtService.MintedToken token =
                jwtService.mintSession(userId, "alice", "MEMBER");

        assertNotNull(token.token());
        assertNotNull(token.jti());
        assertNotNull(token.expiresAt());

        Claims claims = jwtService.parseAndValidate(token.token());

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals("alice", claims.get("username"));
        assertEquals("MEMBER", claims.get("role"));
        assertEquals(userId, jwtService.extractUserId(token.token()));
        assertEquals("alice", jwtService.extractUsername(token.token()));
        assertEquals("MEMBER", jwtService.extractRole(token.token()));
        assertTrue(jwtService.isValid(token.token()));
    }

    @Disabled
    @Test
    public void jwtService_mintSession_invalidArgumentsThrow() {
        JwtService jwtService = new JwtService(JWT_SECRET, 60_000, mock(ITokenBlacklist.class));

        assertThrows(
                IllegalArgumentException.class,
                () -> jwtService.mintSession(null, "alice", "MEMBER")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> jwtService.mintSession(UUID.randomUUID(), "alice", null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> jwtService.mintSession(UUID.randomUUID(), "alice", "   ")
        );
    }

    @Disabled
    @Test
    public void jwtService_parseAndValidate_revokedTokenThrowsAndIsInvalid() {
        ITokenBlacklist blacklist = mock(ITokenBlacklist.class);
        JwtService jwtService = new JwtService(JWT_SECRET, 60_000, blacklist);

        JwtService.MintedToken token =
                jwtService.mintSession(UUID.randomUUID(), "bob", "MEMBER");

        when(blacklist.isRevoked(token.jti())).thenReturn(true);

        assertThrows(
                JwtException.class,
                () -> jwtService.parseAndValidate(token.token())
        );

        assertFalse(jwtService.isValid(token.token()));
    }

    @Disabled
    @Test
    public void jwtService_parseAllowingExpired_returnsClaimsUnlessRevoked() {
        ITokenBlacklist blacklist = mock(ITokenBlacklist.class);
        JwtService jwtService = new JwtService(JWT_SECRET, -1_000, blacklist);

        UUID userId = UUID.randomUUID();
        JwtService.MintedToken expired =
                jwtService.mintSession(userId, "expired-user", "MEMBER");

        when(blacklist.isRevoked(expired.jti())).thenReturn(false);

        Claims claims = jwtService.parseAllowingExpired(expired.token());

        assertNotNull(claims);
        assertEquals(userId.toString(), claims.getSubject());

        when(blacklist.isRevoked(expired.jti())).thenReturn(true);

        assertNull(jwtService.parseAllowingExpired(expired.token()));
    }


    @Disabled
    @Test
    public void jwtService_parseAllowingExpired_badInputReturnsNull() {
        JwtService jwtService = new JwtService(JWT_SECRET, 60_000, mock(ITokenBlacklist.class));

        assertNull(jwtService.parseAllowingExpired(null));
        assertNull(jwtService.parseAllowingExpired("   "));
        assertNull(jwtService.parseAllowingExpired("not-a-real-jwt"));
        assertFalse(jwtService.isValid("not-a-real-jwt"));
    }

     */

    @Test
    public void mintedToken_constructorGuards() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService.MintedToken(null, "jti", Instant.now())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService.MintedToken("   ", "jti", Instant.now())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService.MintedToken("token", null, Instant.now())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService.MintedToken("token", "   ", Instant.now())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new JwtService.MintedToken("token", "jti", null)
        );
    }

    // ================================================================
    // ActiveSession
    // ================================================================

    @Test
    public void activeSession_validatesFieldsAndKeepsValues() {
        Instant expiresAt = Instant.now().plusSeconds(60);

        ActiveSession session = new ActiveSession("jti-1", expiresAt);

        assertEquals("jti-1", session.jti());
        assertEquals(expiresAt, session.expiresAt());
    }

    @Test
    public void activeSession_invalidFieldsThrow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ActiveSession(null, Instant.now())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ActiveSession("   ", Instant.now())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ActiveSession("jti", null)
        );
    }

    // ================================================================
    // NotificationService
    // ================================================================

    @Test
    public void notificationService_constructorGuards() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NotificationService(null, notificationRepository)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new NotificationService(notifier, null)
        );
    }

    @Test
    public void notificationService_notifyByUuid_delegatesToStringNotifier() {
        UUID userId = UUID.randomUUID();

        when(notifier.notifyUser(userId.toString(), "hello")).thenReturn(true);

        boolean delivered = notificationService.notifyUser(userId, "hello");

        assertTrue(delivered);
        verify(notifier).notifyUser(userId.toString(), "hello");
    }

    @Test
    public void notificationService_notifyByString_validationAndDeliveryBranches() {
        when(notifier.notifyUser("user1", "delivered")).thenReturn(true);
        when(notifier.notifyUser("user1", "queued")).thenReturn(false);

        assertTrue(notificationService.notifyUser("user1", "delivered"));
        assertFalse(notificationService.notifyUser("user1", "queued"));

        assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.notifyUser((String) null, "msg")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.notifyUser("   ", "msg")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.notifyUser("user1", null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.notifyUser("user1", "   ")
        );
    }

    @Test
    public void notificationService_notifyWithType_fallsBackToPlainNotifierWhenNotConcreteNotifier() {
        when(notifier.notifyUser("user1", "typed message")).thenReturn(false);

        boolean delivered =
                notificationService.notifyUser("user1", null, "typed message", "/target");

        assertFalse(delivered);
        verify(notifier).notifyUser("user1", "typed message");
    }

    @Test
    public void notificationService_repositoryOperationsDelegateAndValidate() {
        UUID notificationId = UUID.randomUUID();

        when(notificationRepository.findAllByRecipient("user1")).thenReturn(List.of());
        when(notificationRepository.findUnreadByRecipient("user1")).thenReturn(List.of());
        when(notificationRepository.markAllAsRead("user1")).thenReturn(3);

        assertTrue(notificationService.getAllNotifications("user1").isEmpty());
        assertTrue(notificationService.getUnreadNotifications("user1").isEmpty());

        notificationService.markAsRead("user1", notificationId);
        assertEquals(3, notificationService.markAllAsRead("user1"));

        verify(notificationRepository).findAllByRecipient("user1");
        verify(notificationRepository).findUnreadByRecipient("user1");
        verify(notificationRepository).markAsRead("user1", notificationId);
        verify(notificationRepository).markAllAsRead("user1");

        assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.getAllNotifications(" ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.getUnreadNotifications(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.markAsRead("user1", null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> notificationService.markAllAsRead("")
        );
    }

    // ================================================================
    // EventPublisher
    // ================================================================

    @Test
    public void eventPublisher_subscribeAndPublishDeliversToAllListeners() {
        EventPublisher publisher = new EventPublisher();
        IDomainEvent event = mock(IDomainEvent.class);

        List<IDomainEvent> first = new java.util.ArrayList<>();
        List<IDomainEvent> second = new java.util.ArrayList<>();

        publisher.subscribe(first::add);
        publisher.subscribe(second::add);

        publisher.publish(event);

        assertEquals(List.of(event), first);
        assertEquals(List.of(event), second);
    }

    @Test
    public void eventPublisher_guardsAgainstNulls() {
        EventPublisher publisher = new EventPublisher();

        assertThrows(
                IllegalArgumentException.class,
                () -> publisher.subscribe(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> publisher.publish(null)
        );
    }

    // ================================================================
    // EventListener
    // ================================================================

    @Test
    public void eventListener_constructorGuard() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new EventListener(null)
        );
    }

    @Test
    public void eventListener_lotteryWonEvent_notifiesWinnerWithCode() {
        NotificationService service = mock(NotificationService.class);
        EventListener listener = new EventListener(service);

        UUID eventId = UUID.randomUUID();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(10);

        listener.handle(new LotteryWonEvent("winner-user", eventId, "CODE-123", expiry));

        verify(service).notifyUser(
                eq("winner-user"),
                contains("CODE-123")
        );
    }

    @Test
    public void eventListener_purchaseCompletedEvent_notifiesBuyer() {
        NotificationService service = mock(NotificationService.class);
        EventListener listener = new EventListener(service);

        UUID userId = UUID.randomUUID();

        listener.handle(new PurchaseCompletedEvent(userId));

        verify(service).notifyUser(
                eq(userId.toString()),
                contains("completed")
        );
    }

    @Test
    public void eventListener_unknownEventIsIgnored() {
        NotificationService service = mock(NotificationService.class);
        EventListener listener = new EventListener(service);

        listener.handle(mock(IDomainEvent.class));

        verifyNoInteractions(service);
    }

    // ================================================================
    // QueueManager
    // ================================================================

    @Test
    public void queueManager_settingsGuardsAndSnapshotsAndClearQueue() {
        QueueManager manager = new QueueManager(notifier);

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.setMaxConcurrentSelectors(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> manager.setHowManyMinutesToStartSelection(0)
        );

        manager.setMaxConcurrentSelectors(1);
        manager.setHowManyMinutesToStartSelection(1);

        UUID eventId = UUID.randomUUID();
        UUID activeUser = UUID.randomUUID();
        UUID waitingUser = UUID.randomUUID();

        assertTrue(manager.requestSelectionAccess(activeUser, eventId).isAllowed());
        assertFalse(manager.requestSelectionAccess(waitingUser, eventId).isAllowed());

        QueueManager.QueueSnapshot snapshot = manager.getQueueSnapshot(eventId);

        assertEquals(eventId, snapshot.eventId());
        assertEquals(1, snapshot.queueSize());
        assertEquals(1, snapshot.activeSelectorsCount());
        assertEquals(1, snapshot.maxConcurrentSelectors());
        assertEquals(1, snapshot.minutesToStartSelection());
        assertEquals(1, manager.getAllQueueSnapshots().size());

        manager.clearQueue(eventId);

        QueueManager.QueueSnapshot cleared = manager.getQueueSnapshot(eventId);
        assertEquals(0, cleared.queueSize());
        assertEquals(0, cleared.activeSelectorsCount());
        assertEquals(0, manager.getAllQueueSnapshots().size());
    }

    @Test
    public void queueManager_releaseBatchWhenCapacityFullReturnsEmpty() {
        QueueManager manager = new QueueManager(notifier);
        manager.setMaxConcurrentSelectors(1);

        UUID eventId = UUID.randomUUID();
        UUID activeUser = UUID.randomUUID();
        UUID waitingUser = UUID.randomUUID();

        assertTrue(manager.requestSelectionAccess(activeUser, eventId).isAllowed());
        assertFalse(manager.requestSelectionAccess(waitingUser, eventId).isAllowed());

        List<UUID> released = manager.releaseBatch(eventId, 1);

        assertTrue(released.isEmpty());
        assertEquals(1, manager.getQueueSize(eventId));
    }

    @Test
    public void queueManager_releaseBatchNotifiesReleasedUsers() {
        QueueManager manager = new QueueManager(notifier);
        manager.setMaxConcurrentSelectors(1);

        UUID eventId = UUID.randomUUID();
        UUID activeUser = UUID.randomUUID();
        UUID waitingUser = UUID.randomUUID();

        manager.requestSelectionAccess(activeUser, eventId);
        manager.requestSelectionAccess(waitingUser, eventId);

        manager.finishAccess(activeUser, eventId);

        List<UUID> released = manager.releaseBatch(eventId, 1);

        assertEquals(List.of(waitingUser), released);
        assertTrue(manager.hasSelectAccess(waitingUser, eventId));
        verify(notifier).notifyUser(eq(waitingUser), contains("Your turn has arrived"));
    }

    @Test
    public void queueManager_statusForUserNotInQueueDoesNotEnqueue() {
        QueueManager manager = new QueueManager();

        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        QueueAccessResult status = manager.getSelectionAccessStatus(userId, eventId);

        assertFalse(status.isAllowed());
        assertEquals(-1, status.getUserPositionInQueue());
        assertEquals(0, status.getQueueSize());
        assertEquals(0, manager.getQueueSize(eventId));
    }

    @Test
    public void queueManager_nullArgumentsThrowDomainExceptions() {
        QueueManager manager = new QueueManager();

        assertThrows(
                RuntimeException.class,
                () -> manager.requestSelectionAccess(null, UUID.randomUUID())
        );

        assertThrows(
                RuntimeException.class,
                () -> manager.requestSelectionAccess(UUID.randomUUID(), null)
        );

        assertThrows(
                RuntimeException.class,
                () -> manager.requireSelectionAccess(UUID.randomUUID(), null)
        );
    }

    // ================================================================
    // CompanyService extra branches
    // ================================================================

    @Test
    public void companyService_invitationAndMembershipValidationAndDelegation() {
        UUID companyId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();

        companyService.acceptCompanyInvitation(invitationId, "user1", companyId);
        companyService.rejectCompanyInvitation(invitationId, "user1", companyId);

        verify(rolesDomainService).acceptCompanyInvitation(invitationId, "user1", companyId);
        verify(rolesDomainService).rejectCompanyInvitation(invitationId, "user1", companyId);

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.acceptCompanyInvitation(null, "user1", companyId)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.acceptCompanyInvitation(invitationId, " ", companyId)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.acceptCompanyInvitation(invitationId, "user1", null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.rejectCompanyInvitation(null, "user1", companyId)
        );
    }

    @Test
    public void companyService_getUserInvitationsAndMemberRemovalBranches() {
        UUID companyId = UUID.randomUUID();

        when(rolesDomainService.getUserInvitations("user@example.com"))
                .thenReturn(List.of());

        assertTrue(companyService.getUserInvitations("user@example.com").isEmpty());

        companyService.removeCompanyMemberAsOwner("owner", companyId, "member");
        companyService.removeCompanyMemberAsAdmin("admin", "member");

        verify(rolesDomainService).removeCompanyMemberAsOwner("owner", companyId, "member");
        verify(rolesDomainService).removeCompanyMemberAsAdmin("admin", "member");

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.getUserInvitations(" ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.removeCompanyMemberAsOwner(" ", companyId, "member")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.removeCompanyMemberAsOwner("owner", companyId, " ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.removeCompanyMemberAsAdmin(null, "member")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.removeCompanyMemberAsAdmin("admin", "")
        );
    }

    @Test
    public void companyService_changePermissionsAndDiscountValidationBranches() {
        UUID companyId = UUID.randomUUID();
        Set<CompanyPermission> permissions = Set.of(CompanyPermission.MANAGE_POLICIES);

        companyService.changeManagerPermissions(
                "owner",
                companyId,
                "manager",
                permissions
        );

        verify(rolesDomainService).changeManagerPermissions(
                "owner",
                companyId,
                "manager",
                permissions
        );
        verify(notifier).notifyUser("manager", "Your manager permissions have changed.");

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.changeManagerPermissions(" ", companyId, "manager", permissions)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.changeManagerPermissions("owner", companyId, " ", permissions)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.addPolicyRule(
                        "owner",
                        companyId,
                        Optional.of(-1f),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        true
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.addPolicyRule(
                        "owner",
                        companyId,
                        Optional.empty(),
                        Optional.of(-1),
                        Optional.empty(),
                        Optional.empty(),
                        true
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.addPolicyRule(
                        "owner",
                        companyId,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(-1),
                        Optional.empty(),
                        true
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.deletePolicyRule("owner", companyId, null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.addOvertDiscount(
                        "owner",
                        companyId,
                        LocalDate.now(),
                        null,
                        10f
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.addOvertDiscount(
                        "owner",
                        companyId,
                        LocalDate.now(),
                        LocalDate.now().minusDays(1),
                        10f
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.addConditionalDiscount(
                        "owner",
                        companyId,
                        LocalDate.now(),
                        LocalDate.now().plusDays(1),
                        10f,
                        -1,
                        1
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.addConditionalDiscount(
                        "owner",
                        companyId,
                        LocalDate.now(),
                        LocalDate.now().plusDays(1),
                        10f,
                        1,
                        -1
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.addCouponCode(
                        "owner",
                        companyId,
                        LocalDate.now(),
                        LocalDate.now().plusDays(1),
                        10f,
                        " "
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.removeDiscount("owner", companyId, null)
        );
    }

    @Test
    public void companyService_getCompanyPoliciesConvertsPurchaseRules() {
        UUID companyId = UUID.randomUUID();
        Company company = new Company("founder", "Acme");

        company.addPurchasePolicy(
                Optional.of(18f),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true
        );

        company.addPurchasePolicy(
                Optional.empty(),
                Optional.of(2),
                Optional.empty(),
                Optional.empty(),
                true
        );

        when(rolesDomainService.getCompany(companyId)).thenReturn(company);

        assertNotNull(companyService.getCompanyPolicies(companyId, "owner@example.com"));

        verify(rolesDomainService).getCompanyAccess(companyId, "owner@example.com");
        verify(rolesDomainService).getCompany(companyId);

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.getCompanyPolicies(null, "owner@example.com")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.getCompanyPolicies(companyId, " ")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.getCompanyAccess(null, "owner@example.com")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.getCompanyAccess(companyId, null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.getUserCompanies(" ")
        );
    }

    // ================================================================
    // EventService extra branches
    // ================================================================

    @Test
    public void eventService_updateSittingAreaAndDeleteAreaDelegateAndValidate() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();

        eventService.updateSittingArea(
                "owner",
                companyId,
                eventId,
                areaId,
                50.0,
                2,
                3
        );

        verify(eventDomainService).updateSittingArea(
                "owner",
                companyId,
                eventId,
                areaId,
                50.0,
                2,
                3
        );

        eventService.deleteArea("owner", companyId, eventId, areaId);

        verify(eventDomainService).deleteArea("owner", companyId, eventId, areaId);

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.updateSittingArea(" ", companyId, eventId, areaId, 50.0, 1, 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.updateSittingArea("owner", null, eventId, areaId, 50.0, 1, 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.updateSittingArea("owner", companyId, eventId, areaId, -1.0, 1, 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.updateSittingArea("owner", companyId, eventId, areaId, 1.0, 0, 1)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.updateSittingArea("owner", companyId, eventId, areaId, 1.0, 1, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.deleteArea("owner", companyId, eventId, null)
        );
    }

    @Test
    public void eventService_addStandingAndSittingAreasCreateAreasAndTickets() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        Event event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(10),
                "Tel Aviv",
                "Artist",
                "concert",
                EventStatus.ACTIVE
        );

        when(eventDomainService.getEventForView(eventId)).thenReturn(event);

        eventService.addStandingArea(eventId, 25.0, 10);
        eventService.addSittingArea(eventId, 40.0, 3, 4);

        assertEquals(2, event.getLayout().getAreasView().size());

        verify(eventDomainService).addStandingTickets(eq(eventId), any(UUID.class), eq(10));
        verify(eventDomainService).addSittingTickets(eq(eventId), any(UUID.class), eq(3), eq(4));

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.addStandingArea(null, 25.0, 10)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.addStandingArea(eventId, -1.0, 10)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.addStandingArea(eventId, 25.0, 0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.addSittingArea(eventId, -1.0, 3, 4)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.addSittingArea(eventId, 25.0, 0, 4)
        );
    }

    @Test
    public void eventService_lotteryAndSaleMethodsDelegateAndValidate() {
        UUID eventId = UUID.randomUUID();

        LocalDateTime open = LocalDateTime.now().plusDays(1);
        LocalDateTime close = LocalDateTime.now().plusDays(2);

        eventService.createLotteryForEvent(eventId, open, close);
        eventService.startRegularSale(eventId);

        verify(eventDomainService).createLotteryForEvent(eventId, open, close);
        verify(eventDomainService).startRegularSale(eventId);

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.createLotteryForEvent(null, open, close)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.createLotteryForEvent(eventId, null, close)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.createLotteryForEvent(eventId, open, null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.createLotteryForEvent(eventId, close, open)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.startRegularSale(null)
        );
    }

    @Test
    public void eventService_getEventDetailsCoversInventoryPolicyDiscountAndLotteryMapping() {
        UUID eventId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID standingAreaId = UUID.randomUUID();
        UUID sittingAreaId = UUID.randomUUID();

        Event event = new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(10),
                "Haifa",
                "Artist",
                "festival",
                EventStatus.ACTIVE
        );
        event.setName("Festival");
        event.addTag("rock");

        event.getLayout().addArea(new StandingArea(standingAreaId, 30.0));
        event.getLayout().addArea(new SittingArea(sittingAreaId, 80.0));

        event.addTicket(new StandingTicket(UUID.randomUUID(), eventId, standingAreaId, 30.0f));
        event.addTicket(new SittingTicket(UUID.randomUUID(), eventId, sittingAreaId, 80.0f, 1, 1));

        event.addPurchasePolicy(
                Optional.of(18f),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true
        );

        event.addOvertDiscount(
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                10f
        );

        Company company = new Company("founder", "Company");
        company.addOvertDiscount(
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                5f
        );

        when(eventDomainService.getEventForView(eventId)).thenReturn(event);
        when(eventDomainService.findCompanyById(companyId)).thenReturn(company);
        when(eventDomainService.areLotteryWinnersDrawn(eventId)).thenReturn(true);

        assertNotNull(eventService.getEventDetails(eventId));
    }

    @Test
    public void eventService_searchCriteriaValidationBranches() {
        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.searchEvents(EventService.toDomainCriteria(
                        null,
                        null,
                        -1.0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.searchEvents(EventService.toDomainCriteria(
                        null,
                        null,
                        10.0,
                        5.0,
                        null,
                        null,
                        null,
                        null,
                        null
                ))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.searchEvents(EventService.toDomainCriteria(
                        null,
                        null,
                        null,
                        null,
                        LocalDateTime.now().plusDays(2),
                        LocalDateTime.now(),
                        null,
                        null,
                        null
                ))
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> eventService.searchEvents(EventService.toDomainCriteria(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        6.0,
                        null,
                        null
                ))
        );
    }

    // ================================================================
    // PurchaseService extra branches
    // ================================================================

    @Test
    public void purchaseService_completePurchasePublishesEventAndNotifiesSoldOutManager() {
        UUID activePurchaseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        ActivePurchase activePurchase = mock(ActivePurchase.class);

        when(activePurchase.getUserID()).thenReturn(userId);
        when(activePurchase.getEventID()).thenReturn(eventId);
        when(purchaseDomainService.viewActivePurchase(activePurchaseId)).thenReturn(activePurchase);
        when(purchaseDomainService.completePurchase(eq(activePurchaseId), any(PaymentDetails.class), eq("SAVE10")))
                .thenReturn(true);
        when(purchaseDomainService.getEventManager(eventId)).thenReturn("manager1");

        purchaseService.completePurchase(
                activePurchaseId,
                mock(PaymentDetails.class),
                "  SAVE10  "
        );

        verify(notifier).notifyUser(userId, "Purchase Complete");
        verify(notifier).notifyUser(eq("manager1"), contains("SOLD OUT"));
        verify(eventPublisher).publish(any(PurchaseCompletedEvent.class));
    }

    @Test
    public void purchaseService_completePurchaseValidationBranches() {
        assertThrows(
                IllegalArgumentException.class,
                () -> purchaseService.completePurchase(null, mock(PaymentDetails.class), null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> purchaseService.completePurchase(UUID.randomUUID(), null, null)
        );
    }

    @Test
    public void purchaseService_drawLotteryPublishesAndNotifiesUuidAndStringWinners() {
        UUID eventId = UUID.randomUUID();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(10);
        UUID uuidWinner = UUID.randomUUID();

        Map<String, String> winners = new LinkedHashMap<>();
        winners.put(uuidWinner.toString(), "UUID-CODE");
        winners.put("usernameWinner", "USER-CODE");

        Event event = new Event(
                eventId,
                UUID.randomUUID(),
                LocalDateTime.now().plusDays(1),
                "Tel Aviv",
                "Artist",
                "concert",
                EventStatus.ACTIVE
        );
        event.setName("Concert Name");

        when(purchaseDomainService.drawLotteryForEvent(eventId, expiry)).thenReturn(winners);
        when(purchaseDomainService.findEventById(eventId)).thenReturn(event);

        Map<String, String> result = purchaseService.drawLotteryForEvent(eventId, expiry);

        assertEquals(winners, result);
        verify(eventPublisher, times(2)).publish(any(LotteryWonEvent.class));
        verify(notifier).notifyUser(eq(uuidWinner), contains("UUID-CODE"));
        verify(notifier).notifyUser(eq("usernameWinner"), contains("USER-CODE"));

        assertThrows(
                IllegalArgumentException.class,
                () -> purchaseService.drawLotteryForEvent(null, expiry)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> purchaseService.drawLotteryForEvent(eventId, null)
        );
    }

    @Test
    public void purchaseService_sendProducerMessageNotifiesDistinctBuyersOnly() {
        UUID eventId = UUID.randomUUID();
        UUID buyer1 = UUID.randomUUID();
        UUID buyer2 = UUID.randomUUID();

        PurchaseHistory h1 = mock(PurchaseHistory.class);
        PurchaseHistory h2 = mock(PurchaseHistory.class);
        PurchaseHistory h3 = mock(PurchaseHistory.class);

        when(h1.getUserId()).thenReturn(buyer1);
        when(h2.getUserId()).thenReturn(buyer1);
        when(h3.getUserId()).thenReturn(buyer2);

        when(purchaseDomainService.getHistoryByEvent(eventId))
                .thenReturn(List.of(h1, h2, h3));

        purchaseService.sendProducerMessageToEventBuyers(eventId, "hello buyers");

        verify(notifier, times(1)).notifyUser(buyer1, "hello buyers");
        verify(notifier, times(1)).notifyUser(buyer2, "hello buyers");

        assertThrows(
                IllegalArgumentException.class,
                () -> purchaseService.sendProducerMessageToEventBuyers(null, "msg")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> purchaseService.sendProducerMessageToEventBuyers(eventId, " ")
        );
    }

    @Test
    public void purchaseService_viewActivePurchaseBranches() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID activePurchaseId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        ActivePurchase activePurchase = mock(ActivePurchase.class);
        Map<UUID, Float> ticketPrices = Map.of(ticketId, 100f);

        when(activePurchase.getActivePurchaseId()).thenReturn(activePurchaseId);
        when(activePurchase.getUserID()).thenReturn(userId);
        when(activePurchase.getEventID()).thenReturn(eventId);
        when(activePurchase.getTicketIDs()).thenReturn(ticketPrices);
        when(activePurchase.getEndTime()).thenReturn(LocalDateTime.now().plusMinutes(10));
        when(activePurchase.getGuestAgeConfirmed()).thenReturn(true);
        when(activePurchase.getLastUpdate()).thenReturn(LocalDateTime.now());

        Event event = new Event(
                eventId,
                UUID.randomUUID(),
                LocalDateTime.now().plusDays(1),
                "Jerusalem",
                "Artist",
                "concert",
                EventStatus.ACTIVE
        );
        event.setName("Event Name");

        when(purchaseDomainService.findActivePurchaseByUserAndEvent(userId, eventId))
                .thenReturn(activePurchase);
        when(purchaseDomainService.findActivePurchasesByUser(userId))
                .thenReturn(List.of(activePurchase));
        when(purchaseDomainService.viewActivePurchase(activePurchaseId))
                .thenReturn(activePurchase);
        when(purchaseDomainService.findEventById(eventId))
                .thenReturn(event);

        assertNotNull(purchaseService.viewActivePurchase(activePurchaseId));
        assertNotNull(purchaseService.viewActivePurchaseForEvent(userId, eventId));
        assertEquals(1, purchaseService.viewActivePurchasesForUser(userId).size());

        when(purchaseDomainService.findActivePurchaseByUserAndEvent(userId, eventId))
                .thenReturn(null);

        assertNull(purchaseService.viewActivePurchaseForEvent(userId, eventId));

        assertThrows(
                IllegalArgumentException.class,
                () -> purchaseService.viewActivePurchase(null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> purchaseService.viewActivePurchaseForEvent(null, eventId)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> purchaseService.viewActivePurchaseForEvent(userId, null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> purchaseService.viewActivePurchasesForUser(null)
        );
    }

    // ================================================================
    // UserService extra branches
    // ================================================================

    @Test
    public void userService_enterGuestGetByIdAndAdminMessageBranches() {
        IUserRepository userRepository = mock(IUserRepository.class);
        IAuthenticationGateway authGateway = mock(IAuthenticationGateway.class);
        INotifier userNotifier = mock(INotifier.class);
        EventPublisher userPublisher = mock(EventPublisher.class);

        UserService service = new UserService(
                userRepository,
                authGateway,
                userNotifier,
                new InMemoryKeyedLock<>(),
                userPublisher
        );

        assertNotNull(service.enterAsGuest());
        verify(userRepository).add(any(User.class));

        UUID userId = UUID.randomUUID();
        User user = new User(userId, "user", "user@example.com", "hash", 21);

        when(userRepository.getUser(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsAdmin(userId)).thenReturn(false);

        assertEquals(userId, service.getUserById(userId).userId);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getUserById(null)
        );

        when(userRepository.getUser(UUID.randomUUID())).thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getUserById(UUID.randomUUID())
        );

        when(userRepository.isSystemAdmin("admin")).thenReturn(true);
        when(userRepository.getAllUsers()).thenReturn(Map.of(userId, user));

        service.adminMessage("admin", "system message");

        verify(userNotifier).notifyUser(userId, "system message");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.adminMessage(null, "message")
        );

        when(userRepository.isSystemAdmin("not-admin")).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.adminMessage("not-admin", "message")
        );
    }

    // ================================================================
    // ActivePurchaseCleaner
    // ================================================================


    @Ignore
    @Test
    public void activePurchaseCleaner_warnsUserWhenPurchaseIsCloseToExpiry() throws Exception {
        PurchaseService purchaseServiceMock = mock(PurchaseService.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);

        UUID purchaseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ActivePurchase purchase = mock(ActivePurchase.class);

        when(purchase.getActivePurchaseId()).thenReturn(purchaseId);
        when(purchase.getUserID()).thenReturn(userId);
        when(purchase.isExpired(any(LocalDateTime.class))).thenReturn(false);
        when(purchase.getEndTime()).thenReturn(LocalDateTime.now().plusSeconds(30));
        when(purchaseRepository.findAll()).thenReturn(List.of(purchase));

        ActivePurchaseCleaner cleaner =
                new ActivePurchaseCleaner(purchaseServiceMock, purchaseRepository, notifier, Duration.ofSeconds(1), 1);

        cleaner.start();

        verify(notifier, timeout(1200).times(1))
                .notifyUser(eq(userId), contains("Active Order is about to be canceled"));

        cleaner.interrupt();
        cleaner.join(1500);
    }

    // ================================================================
    // LotteryScheduler
    // ================================================================

}