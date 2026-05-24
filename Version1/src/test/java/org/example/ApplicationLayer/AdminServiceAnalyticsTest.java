package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.AdminDTOs.AdminAnalyticsDTO;
import org.example.DomainLayer.IAdminRepository;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.IPurchaseRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.RolesDomainService;
import org.example.DomainLayer.AdminAggregate.SystemAnalyticsSnapshot;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserStatus;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TDD — RED phase.
 *
 * <h2>What is under test</h2>
 * <p>The analytics slice of {@link AdminService}, specifically the
 * {@code getAnalytics()} method.  These tests pin down <em>only</em> the
 * new behaviour introduced by the analytics feature; existing AdminService
 * behaviour is covered in other test classes.
 *
 * <h2>Why these tests fail initially</h2>
 * <ul>
 *   <li>{@code ISystemMetricsTracker} does not exist yet — the mock cannot
 *       be created.</li>
 *   <li>{@code AdminService} does not accept an {@code ISystemMetricsTracker}
 *       constructor argument yet — compilation fails.</li>
 *   <li>{@code AdminAnalyticsDTO} is missing the three rate fields
 *       ({@code newSubscriberRatePerMin}, {@code ticketReservationRatePerMin},
 *       {@code ticketPurchaseRatePerMin}) — field accesses fail to compile.</li>
 *   <li>{@code SystemAnalyticsSnapshot} is missing the same three fields —
 *       verification of snapshot contents fails.</li>
 * </ul>
 *
 * <h2>Design invariants verified</h2>
 * <ul>
 *   <li>Rate fields in the returned DTO come <em>exclusively</em> from
 *       {@code ISystemMetricsTracker} — never recomputed from raw data.</li>
 *   <li>Count fields (active visitors, active queues) continue to be derived
 *       from their existing sources ({@code IUserRepository},
 *       {@code QueueManager}) and are unaffected by the tracker.</li>
 *   <li>Admin authorisation is still enforced before any computation.</li>
 *   <li>A {@code SystemAnalyticsSnapshot} carrying all five metric values is
 *       persisted via {@code IAdminRepository} on every call.</li>
 * </ul>
 */
public class AdminServiceAnalyticsTest {

    // ── collaborator mocks ────────────────────────────────────────────────
    private IUserRepository       userRepositoryMock;
    private ICompanyRepository    companyRepositoryMock;
    private IPurchaseRepository   purchaseRepositoryMock;
    private IHistoryRepository    historyRepositoryMock;
    private IAdminRepository      adminRepositoryMock;
    private RolesDomainService    rolesDomainServiceMock;
    private PurchaseDomainService purchaseDomainServiceMock;
    private QueueManager          queueManagerMock;
    private INotifier             notifierMock;
    private ISystemMetricsTracker metricsTrackerMock;   // NEW — does not exist yet

    private AdminService adminService;

    // ── test fixtures ─────────────────────────────────────────────────────
    private static final UUID   ADMIN_ID       = UUID.randomUUID();
    private static final String ADMIN_USERNAME = "system_admin";

    @Before
    public void setUp() {
        userRepositoryMock    = mock(IUserRepository.class);
        companyRepositoryMock = mock(ICompanyRepository.class);
        purchaseRepositoryMock = mock(IPurchaseRepository.class);
        historyRepositoryMock  = mock(IHistoryRepository.class);
        adminRepositoryMock    = mock(IAdminRepository.class);
        rolesDomainServiceMock = mock(RolesDomainService.class);
        purchaseDomainServiceMock = mock(PurchaseDomainService.class);
        queueManagerMock       = mock(QueueManager.class);
        notifierMock           = mock(INotifier.class);
        metricsTrackerMock     = mock(ISystemMetricsTracker.class);

        // Wire the admin as valid by default — override per-test when needed
        when(userRepositoryMock.existsAdmin(ADMIN_ID)).thenReturn(true);
        when(userRepositoryMock.isSystemAdmin(ADMIN_USERNAME)).thenReturn(true);

        // Sensible defaults so getAnalytics() can complete without NPEs
        when(userRepositoryMock.getAllUsers()).thenReturn(Collections.emptyMap());
        when(companyRepositoryMock.getAllActive()).thenReturn(Collections.emptyList());
        when(purchaseRepositoryMock.findAll()).thenReturn(Collections.emptyList());
        when(historyRepositoryMock.getAll()).thenReturn(Collections.emptyList());
        when(queueManagerMock.getAllQueueSnapshots()).thenReturn(Collections.emptyList());

        // Tracker defaults — zero rates unless overridden in a specific test
        when(metricsTrackerMock.getSubscriptionRatePerMinute()).thenReturn(0.0);
        when(metricsTrackerMock.getReservationRatePerMinute()).thenReturn(0.0);
        when(metricsTrackerMock.getPurchaseRatePerMinute()).thenReturn(0.0);

        // AdminService constructor will gain ISystemMetricsTracker as its
        // last parameter once Stage 3 is implemented.
        adminService = new AdminService(
                userRepositoryMock,
                companyRepositoryMock,
                purchaseRepositoryMock,
                historyRepositoryMock,
                adminRepositoryMock,
                rolesDomainServiceMock,
                purchaseDomainServiceMock,
                queueManagerMock,
                notifierMock,
                metricsTrackerMock          // NEW parameter — fails to compile until Stage 3
        );
    }

    // ====================================================================
    // 1. Admin authorisation guard (unchanged behaviour, must still hold)
    // ====================================================================

    @Test
    public void getAnalytics_nullAdminId_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> adminService.getAnalytics(null, ADMIN_USERNAME)
        );

        verifyNoInteractions(metricsTrackerMock);
    }

    @Test
    public void getAnalytics_nullAdminUsername_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> adminService.getAnalytics(ADMIN_ID, null)
        );

        verifyNoInteractions(metricsTrackerMock);
    }

    @Test
    public void getAnalytics_blankAdminUsername_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> adminService.getAnalytics(ADMIN_ID, "   ")
        );

        verifyNoInteractions(metricsTrackerMock);
    }

    @Test
    public void getAnalytics_adminIdNotInRepository_throwsIllegalArgumentException() {
        UUID unknownId = UUID.randomUUID();
        when(userRepositoryMock.existsAdmin(unknownId)).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> adminService.getAnalytics(unknownId, ADMIN_USERNAME)
        );

        verifyNoInteractions(metricsTrackerMock);
    }

    @Test
    public void getAnalytics_usernameNotInRepository_throwsIllegalArgumentException() {
        when(userRepositoryMock.isSystemAdmin(ADMIN_USERNAME)).thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME)
        );

        verifyNoInteractions(metricsTrackerMock);
    }

    // ====================================================================
    // 2. Metrics tracker is always queried for a valid admin call
    // ====================================================================

    @Test
    public void getAnalytics_validAdmin_queriesAllThreeRatesFromTracker() {
        adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        verify(metricsTrackerMock, times(1)).getSubscriptionRatePerMinute();
        verify(metricsTrackerMock, times(1)).getReservationRatePerMinute();
        verify(metricsTrackerMock, times(1)).getPurchaseRatePerMinute();
    }

    // ====================================================================
    // 3. Rate fields in the returned DTO reflect exactly what the tracker
    //    returns — no transformation, no rounding
    // ====================================================================

    @Test
    public void getAnalytics_returnsSubscriptionRateFromTracker() {
        double expected = 3.0;
        when(metricsTrackerMock.getSubscriptionRatePerMinute()).thenReturn(expected);

        AdminAnalyticsDTO dto = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        // newSubscriberRatePerMin is a NEW public field on AdminAnalyticsDTO
        assertEquals(expected, dto.newSubscriberRatePerMin, 0.0);
    }

    @Test
    public void getAnalytics_returnsReservationRateFromTracker() {
        double expected = 7.5;
        when(metricsTrackerMock.getReservationRatePerMinute()).thenReturn(expected);

        AdminAnalyticsDTO dto = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        // ticketReservationRatePerMin is a NEW public field on AdminAnalyticsDTO
        assertEquals(expected, dto.ticketReservationRatePerMin, 0.0);
    }

    @Test
    public void getAnalytics_returnsPurchaseRateFromTracker() {
        double expected = 1.25;
        when(metricsTrackerMock.getPurchaseRatePerMinute()).thenReturn(expected);

        AdminAnalyticsDTO dto = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        // ticketPurchaseRatePerMin is a NEW public field on AdminAnalyticsDTO
        assertEquals(expected, dto.ticketPurchaseRatePerMin, 0.0);
    }

    @Test
    public void getAnalytics_allRatesZero_dtoRatesAreZero() {
        // tracker defaults already return 0.0 from setUp()
        AdminAnalyticsDTO dto = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        assertEquals(0.0, dto.newSubscriberRatePerMin,      0.0);
        assertEquals(0.0, dto.ticketReservationRatePerMin,  0.0);
        assertEquals(0.0, dto.ticketPurchaseRatePerMin,     0.0);
    }

    // ====================================================================
    // 4. activeVisitorsCount — derived from LOGGED_IN users (unchanged
    //    behaviour, but now exposed under the semantically correct label)
    // ====================================================================

    @Test
    public void getAnalytics_activeVisitorsCount_equalsNumberOfLoggedInUsers() {
        User loggedIn1 = userWithStatus(UserStatus.LOGGED_IN);
        User loggedIn2 = userWithStatus(UserStatus.LOGGED_IN);
        User notLogged = userWithStatus(UserStatus.NOT_LOGGED_IN);

        Map<UUID, User> allUsers = Map.of(
                loggedIn1.getId(), loggedIn1,
                loggedIn2.getId(), loggedIn2,
                notLogged.getId(), notLogged
        );
        when(userRepositoryMock.getAllUsers()).thenReturn(allUsers);

        AdminAnalyticsDTO dto = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        // loggedInUsersCount is the existing field that maps to "active visitors"
        assertEquals(2, dto.loggedInUsersCount);
    }

    @Test
    public void getAnalytics_noLoggedInUsers_activeVisitorsIsZero() {
        User u1 = userWithStatus(UserStatus.NOT_LOGGED_IN);
        when(userRepositoryMock.getAllUsers()).thenReturn(Map.of(u1.getId(), u1));

        AdminAnalyticsDTO dto = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        assertEquals(0, dto.loggedInUsersCount);
    }

    // ====================================================================
    // 5. activeQueuesCount — sourced from QueueManager (unchanged source)
    // ====================================================================

    @Test
    public void getAnalytics_activeQueuesCount_equalsNumberOfQueueSnapshots() {
        List<QueueManager.QueueSnapshot> snapshots = List.of(
                makeQueueSnapshot(UUID.randomUUID()),
                makeQueueSnapshot(UUID.randomUUID()),
                makeQueueSnapshot(UUID.randomUUID())
        );
        when(queueManagerMock.getAllQueueSnapshots()).thenReturn(snapshots);

        AdminAnalyticsDTO dto = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        assertEquals(3, dto.activeQueuesCount);
    }

    @Test
    public void getAnalytics_noQueues_activeQueuesCountIsZero() {
        when(queueManagerMock.getAllQueueSnapshots()).thenReturn(Collections.emptyList());

        AdminAnalyticsDTO dto = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        assertEquals(0, dto.activeQueuesCount);
    }

    // ====================================================================
    // 6. Snapshot persistence — a SystemAnalyticsSnapshot containing all
    //    five new metric values must be saved to the admin repository
    // ====================================================================

    @Test
    public void getAnalytics_savesSnapshotToAdminRepository() {
        adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        verify(adminRepositoryMock, times(1))
                .saveAnalyticsSnapshot(any(SystemAnalyticsSnapshot.class));
    }

    @Test
    public void getAnalytics_savedSnapshot_containsCorrectRates() {
        double subRate   = 4.0;
        double resRate   = 2.0;
        double purchRate = 8.0;

        when(metricsTrackerMock.getSubscriptionRatePerMinute()).thenReturn(subRate);
        when(metricsTrackerMock.getReservationRatePerMinute()).thenReturn(resRate);
        when(metricsTrackerMock.getPurchaseRatePerMinute()).thenReturn(purchRate);

        adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        // Capture the snapshot that was persisted and verify its rate fields.
        // SystemAnalyticsSnapshot will gain three new getter methods in Stage 3.
        org.mockito.ArgumentCaptor<SystemAnalyticsSnapshot> captor =
                org.mockito.ArgumentCaptor.forClass(SystemAnalyticsSnapshot.class);

        verify(adminRepositoryMock).saveAnalyticsSnapshot(captor.capture());

        SystemAnalyticsSnapshot saved = captor.getValue();
        assertEquals(subRate,   saved.getNewSubscriberRatePerMin(),      0.0);
        assertEquals(resRate,   saved.getTicketReservationRatePerMin(),  0.0);
        assertEquals(purchRate, saved.getTicketPurchaseRatePerMin(),     0.0);
    }

    @Test
    public void getAnalytics_savedSnapshot_containsCorrectActiveVisitors() {
        User u1 = userWithStatus(UserStatus.LOGGED_IN);
        User u2 = userWithStatus(UserStatus.LOGGED_IN);
        when(userRepositoryMock.getAllUsers())
                .thenReturn(Map.of(u1.getId(), u1, u2.getId(), u2));

        adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        org.mockito.ArgumentCaptor<SystemAnalyticsSnapshot> captor =
                org.mockito.ArgumentCaptor.forClass(SystemAnalyticsSnapshot.class);
        verify(adminRepositoryMock).saveAnalyticsSnapshot(captor.capture());

        assertEquals(2, captor.getValue().getLoggedInUsersCount());
    }

    // ====================================================================
    // 7. Return value completeness — the DTO must not be null and all
    //    fields that are consumed by the dashboard must be populated
    // ====================================================================

    @Test
    public void getAnalytics_returnedDto_isNotNull() {
        AdminAnalyticsDTO dto = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        assertNotNull(dto);
    }

    @Test
    public void getAnalytics_returnedDto_hasNonNullTimestamp() {
        AdminAnalyticsDTO dto = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        assertNotNull(dto.createdAt);
    }

    @Test
    public void getAnalytics_rateFields_areNeverNegative() {
        // Tracker returns non-negative values; DTO must reflect them faithfully
        when(metricsTrackerMock.getSubscriptionRatePerMinute()).thenReturn(5.0);
        when(metricsTrackerMock.getReservationRatePerMinute()).thenReturn(0.0);
        when(metricsTrackerMock.getPurchaseRatePerMinute()).thenReturn(12.0);

        AdminAnalyticsDTO dto = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

        assertTrue(dto.newSubscriberRatePerMin     >= 0.0);
        assertTrue(dto.ticketReservationRatePerMin >= 0.0);
        assertTrue(dto.ticketPurchaseRatePerMin    >= 0.0);
    }

    // ====================================================================
    // 8. Interaction order — authorisation check must happen BEFORE any
    //    tracker query so that unauthorised callers never observe metrics
    // ====================================================================

    @Test
    public void getAnalytics_unauthorisedCaller_trackerIsNeverQueried() {
        // Make admin validation fail
        when(userRepositoryMock.existsAdmin(ADMIN_ID)).thenReturn(false);

        try {
            adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);
        } catch (IllegalArgumentException ignored) {
            // expected — admin validation rejects the caller
        }

        verifyNoInteractions(metricsTrackerMock);
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    /**
     * Creates a minimal {@link User} with the given {@link UserStatus}.
     * Only the fields relevant to analytics (id, status) are set;
     * other fields are given placeholder values.
     */
    private User userWithStatus(UserStatus status) {
        User user = new User(
                UUID.randomUUID(),
                "user_" + UUID.randomUUID().toString().substring(0, 8),
                "email_" + UUID.randomUUID() + "@test.com",
                "hashed",
                20
        );

        switch (status) {
            case LOGGED_IN     -> user.login();
            // NOT_LOGGED_IN is the default state after construction
            case NOT_LOGGED_IN -> { /* no-op */ }
            default            -> { /* other statuses not relevant here */ }
        }

        return user;
    }

    /**
     * Creates a minimal {@link QueueManager.QueueSnapshot} record for a
     * given event.  Only the {@code eventId} matters for counting; other
     * values are zeroed.
     */
    private QueueManager.QueueSnapshot makeQueueSnapshot(UUID eventId) {
        return new QueueManager.QueueSnapshot(eventId, 0, 0, 0, 0);
    }
}
