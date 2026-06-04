package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.dto.NotificationDTOs.NotificationDTO;
import org.example.DomainLayer.AdminAggregate.Admin;
import org.example.DomainLayer.AdminAggregate.AdminActionLog;
import org.example.DomainLayer.AdminAggregate.AdminComplaint;
import org.example.DomainLayer.AdminAggregate.AdminComplaintStatus;
import org.example.DomainLayer.AdminAggregate.SystemAnalyticsSnapshot;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.LotteryAggregate.PuchaseLottery;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.NotificationAggregate.Notification;
import org.example.DomainLayer.NotificationAggregate.NotificationType;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.CompanyFounder;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.INotificationRepository;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InfrastructureLayerAdditionalTests {

    private Event event(UUID eventId, UUID companyId) {
        return new Event(
                eventId,
                companyId,
                LocalDateTime.now().plusDays(10),
                "Tel Aviv",
                "Artist",
                "Concert",
                EventStatus.ACTIVE
        );
    }

    // ================================================================
    // AdminRepository
    // ================================================================

    @Test
    public void adminRepository_complaintsLogsAndSnapshotsBranches() {
        AdminRepository repo = new AdminRepository();

        UUID reporterId = UUID.randomUUID();

        AdminComplaint open = new AdminComplaint(
                reporterId,
                "alice",
                "open title",
                "open description"
        );

        AdminComplaint answered = new AdminComplaint(
                UUID.randomUUID(),
                "bob",
                "answered title",
                "answered description"
        );
        answered.respond("admin", "answer");

        repo.saveComplaint(open);
        repo.saveComplaint(answered);

        assertEquals(Optional.of(open), repo.findComplaintById(open.getId()));
        assertEquals(Optional.empty(), repo.findComplaintById(null));
        assertEquals(Optional.empty(), repo.findComplaintById(UUID.randomUUID()));

        assertEquals(2, repo.getAllComplaints().size());
        assertEquals(List.of(open), repo.getOpenComplaints());
        assertEquals(AdminComplaintStatus.OPEN, repo.getOpenComplaints().get(0).getStatus());

        assertThrows(IllegalArgumentException.class, () -> repo.saveComplaint(null));

        AdminActionLog log = new AdminActionLog(
                UUID.randomUUID(),
                "admin",
                "ACTION",
                "target"
        );

        repo.saveActionLog(log);

        assertEquals(List.of(log), repo.getActionLogs());
        assertThrows(IllegalArgumentException.class, () -> repo.saveActionLog(null));

        SystemAnalyticsSnapshot snapshot = new SystemAnalyticsSnapshot(
                1,
                2,
                3,
                4,
                5,
                6,
                7.0,
                8.0,
                9.0
        );

        repo.saveAnalyticsSnapshot(snapshot);

        assertEquals(List.of(snapshot), repo.getAnalyticsSnapshots());
        assertThrows(IllegalArgumentException.class, () -> repo.saveAnalyticsSnapshot(null));
    }

    // ================================================================
    // CompanyRepository
    // ================================================================

    @Test
    public void companyRepository_saveCreateFindActiveAndAllBranches() {
        CompanyRepository repo = new CompanyRepository();

        Company active = new Company("founder1@example.com", "Active Company");
        Company closed = new Company("founder2@example.com", "Closed Company");
        closed.AdminClose();

        repo.save(active);
        repo.save(closed);

        assertEquals(Optional.of(active), repo.findByID(active.getId()));
        assertEquals(Optional.of(closed), repo.findByID(closed.getId()));
        assertEquals(Optional.empty(), repo.findByID(UUID.randomUUID()));

        assertEquals(2, repo.getAll().size());
        assertEquals(List.of(active), repo.getAllActive());

        UUID createdId = repo.createCompany("founder3@example.com", "Created Company");

        assertTrue(repo.findByID(createdId).isPresent());
        assertEquals(3, repo.getAll().size());

        assertThrows(IllegalArgumentException.class, () -> repo.save(null));
    }

    // ================================================================
    // EventRepository / InMemoryEventRepository
    // ================================================================

    @Test
    public void eventRepository_unimplementedMethodsThrow() {
        EventRepository repo = new EventRepository();

        assertThrows(UnsupportedOperationException.class, () -> repo.getById(UUID.randomUUID()));
        assertThrows(UnsupportedOperationException.class, repo::getAll);
        assertThrows(UnsupportedOperationException.class, () -> repo.save(mock(Event.class)));
        assertThrows(UnsupportedOperationException.class, () -> repo.delete(UUID.randomUUID()));
    }

    @Test
    public void inMemoryEventRepository_saveGetDeleteGetAllAndPutBranches() {
        InMemoryEventRepository repo = new InMemoryEventRepository();

        UUID companyId = UUID.randomUUID();
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();

        Event first = event(eventId1, companyId);
        Event second = event(eventId2, companyId);

        assertNull(repo.getById(eventId1));
        assertTrue(repo.getAll().isEmpty());

        repo.save(first);
        repo.put(second);

        assertSame(first, repo.getById(eventId1));
        assertSame(second, repo.getById(eventId2));
        assertEquals(2, repo.getAll().size());

        repo.delete(eventId1);

        assertNull(repo.getById(eventId1));
        assertEquals(1, repo.getAll().size());

        assertThrows(IllegalArgumentException.class, () -> repo.save(null));
    }

    // ================================================================
    // HistoryRepository
    // ================================================================

    @Test
    public void historyRepository_addGetAllFilterByUserAndEvent() {
        HistoryRepository repo = new HistoryRepository();

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID event1 = UUID.randomUUID();
        UUID event2 = UUID.randomUUID();

        PurchaseHistory h1 = mock(PurchaseHistory.class);
        PurchaseHistory h2 = mock(PurchaseHistory.class);
        PurchaseHistory h3 = mock(PurchaseHistory.class);

        when(h1.getUserId()).thenReturn(user1);
        when(h1.getEventId()).thenReturn(event1);

        when(h2.getUserId()).thenReturn(user1);
        when(h2.getEventId()).thenReturn(event2);

        when(h3.getUserId()).thenReturn(user2);
        when(h3.getEventId()).thenReturn(event1);

        repo.add(h1);
        repo.add(h2);
        repo.add(h3);

        assertEquals(List.of(h1, h2, h3), repo.getAll());
        assertEquals(List.of(h1, h2), repo.getByUserId(user1));
        assertEquals(List.of(h3), repo.getByUserId(user2));
        assertEquals(List.of(h1, h3), repo.getByEventId(event1));
        assertEquals(List.of(h2), repo.getByEventId(event2));
        assertTrue(repo.getByUserId(UUID.randomUUID()).isEmpty());
        assertTrue(repo.getByEventId(UUID.randomUUID()).isEmpty());
    }

    // ================================================================
    // InMemoryPurchaseRepository
    // ================================================================

    @Test
    public void inMemoryPurchaseRepository_saveFindDeleteAndNullBranches() {
        InMemoryPurchaseRepository repo = new InMemoryPurchaseRepository();

        UUID purchaseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        ActivePurchase purchase = mock(ActivePurchase.class);

        when(purchase.getActivePurchaseId()).thenReturn(purchaseId);
        when(purchase.getUserID()).thenReturn(userId);
        when(purchase.getEventID()).thenReturn(eventId);

        assertNull(repo.findByID(null));
        assertNull(repo.findByUserID(null));
        assertNull(repo.findByID(purchaseId));
        assertNull(repo.findByUserID(userId));
        assertTrue(repo.findAll().isEmpty());

        repo.save(purchase);

        assertSame(purchase, repo.findByID(purchaseId));
        assertSame(purchase, repo.findByUserID(userId));
        assertEquals(List.of(purchase), repo.findAll());

        assertNull(repo.findByUserID(UUID.randomUUID()));

        repo.deleteByID(null);

        assertEquals(1, repo.findAll().size());

        repo.deleteByID(purchaseId);

        assertNull(repo.findByID(purchaseId));
        assertTrue(repo.findAll().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> repo.save(null));
    }

    // ================================================================
    // LotteryRepository
    // ================================================================

    @Test
    public void lotteryRepository_saveFindByIdFindByEventAndFindAll() {
        LotteryRepository repo = new LotteryRepository();

        UUID lotteryId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        PuchaseLottery lottery = new PuchaseLottery(
                lotteryId,
                eventId,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );

        assertNull(repo.findByID(lotteryId));
        assertNull(repo.findByEventID(eventId));
        assertTrue(repo.findAll().isEmpty());

        repo.save(lottery);

        assertSame(lottery, repo.findByID(lotteryId));
        assertSame(lottery, repo.findByEventID(eventId));
        assertEquals(List.of(lottery), repo.findAll());

        assertNull(repo.findByID(UUID.randomUUID()));
        assertNull(repo.findByEventID(UUID.randomUUID()));
    }

    // ================================================================
    // NotificationRepository
    // ================================================================

    @Test
    public void notificationRepository_saveFindFilterMarkReadAndValidationBranches() {
        NotificationRepository repo = new NotificationRepository();

        Notification n1 = new Notification(
                "user1",
                NotificationType.GENERAL,
                "first",
                "/a"
        );


        Notification n3 = new Notification(
                "user2",
                NotificationType.GENERAL,
                "third",
                "/c"
        );

        repo.save(n1);
        repo.save(n3);

        assertEquals(Optional.of(n1), repo.findById(n1.getId()));
        assertEquals(Optional.empty(), repo.findById(UUID.randomUUID()));


        repo.markAsRead("user1", n1.getId());

        assertTrue(n1.isRead());

        int marked = repo.markAllAsRead("user1");

        assertEquals(0, repo.findUnreadByRecipient("user1").size());

        assertThrows(IllegalArgumentException.class, () -> repo.save(null));
        assertThrows(IllegalArgumentException.class, () -> repo.findById(null));
        assertThrows(IllegalArgumentException.class, () -> repo.findAllByRecipient(null));
        assertThrows(IllegalArgumentException.class, () -> repo.findAllByRecipient(" "));
        assertThrows(IllegalArgumentException.class, () -> repo.findUnreadByRecipient(null));
        assertThrows(IllegalArgumentException.class, () -> repo.markAsRead("user1", UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> repo.markAsRead("wrong-user", n3.getId()));
    }

    // ================================================================
    // Broadcaster
    // ================================================================

    @Test
    public void broadcaster_registerBroadcastUnregisterAndValidationBranches() throws Exception {
        Broadcaster broadcaster = new Broadcaster();

        String userId = "user1";

        NotificationDTO dto = new NotificationDTO(
                new Notification(userId, NotificationType.GENERAL, "hello", null)
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<NotificationDTO> received = new AtomicReference<>();

        Consumer<NotificationDTO> listener = notification -> {
            received.set(notification);
            latch.countDown();
        };

        assertFalse(broadcaster.hasListeners(userId));
        assertFalse(broadcaster.broadcast(userId, dto));

        broadcaster.register(userId, listener);

        assertTrue(broadcaster.hasListeners(userId));
        assertTrue(broadcaster.broadcast(userId, dto));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertSame(dto, received.get());

        broadcaster.unregister(userId, listener);

        assertFalse(broadcaster.hasListeners(userId));
        assertFalse(broadcaster.broadcast(userId, dto));

        broadcaster.unregister("missing-user", listener);

        assertThrows(IllegalArgumentException.class, () -> broadcaster.register(null, listener));
        assertThrows(IllegalArgumentException.class, () -> broadcaster.register(" ", listener));
        assertThrows(IllegalArgumentException.class, () -> broadcaster.register(userId, null));
    }

    // ================================================================
    // Notifier
    // ================================================================

    @Test
    public void notifier_constructorValidationAndNotifyBranches() {
        Broadcaster broadcaster = mock(Broadcaster.class);
        INotificationRepository repository = mock(INotificationRepository.class);

        Notifier notifier = new Notifier(broadcaster, repository);

        when(broadcaster.broadcast(eq("user1"), any(NotificationDTO.class))).thenReturn(true);
        when(broadcaster.broadcast(eq("user2"), any(NotificationDTO.class))).thenReturn(false);

        assertTrue(notifier.notifyUser("user1", "hello"));

        UUID uuidUser = UUID.randomUUID();

        when(broadcaster.broadcast(eq(uuidUser.toString()), any(NotificationDTO.class))).thenReturn(true);

        assertTrue(notifier.notifyUser(uuidUser, "uuid hello"));

        assertThrows(IllegalArgumentException.class, () -> notifier.notifyUser((UUID) null, "msg"));
        assertThrows(IllegalArgumentException.class, () -> notifier.notifyUser((String) null, "msg"));
        assertThrows(IllegalArgumentException.class, () -> notifier.notifyUser(" ", "msg"));
        assertThrows(IllegalArgumentException.class, () -> notifier.notifyUser("user1", null));
        assertThrows(IllegalArgumentException.class, () -> notifier.notifyUser("user1", " "));
    }

    // ================================================================
    // WebSocketNotificationSender
    // ================================================================

    @Test
    public void webSocketNotificationSender_notifyAndValidationBranches() {
        Broadcaster broadcaster = mock(Broadcaster.class);
        WebSocketNotificationSender sender = new WebSocketNotificationSender(broadcaster);

        when(broadcaster.broadcast(eq("user1"), any(NotificationDTO.class))).thenReturn(true);
        when(broadcaster.broadcast(eq("user2"), any(NotificationDTO.class))).thenReturn(false);

        assertTrue(sender.notifyUser("user1", "hello"));
        assertFalse(sender.notifyUser("user2", "hello"));

        UUID uuidUser = UUID.randomUUID();

        when(broadcaster.broadcast(eq(uuidUser.toString()), any(NotificationDTO.class))).thenReturn(true);

        assertTrue(sender.notifyUser(uuidUser, "uuid hello"));

        assertThrows(IllegalArgumentException.class, () -> sender.notifyUser((UUID) null, "msg"));
        assertThrows(IllegalArgumentException.class, () -> sender.notifyUser((String) null, "msg"));
        assertThrows(IllegalArgumentException.class, () -> sender.notifyUser(" ", "msg"));
        assertThrows(IllegalArgumentException.class, () -> sender.notifyUser("user1", null));
        assertThrows(IllegalArgumentException.class, () -> sender.notifyUser("user1", " "));
    }

    // ================================================================
    // UserRepository
    // ================================================================

    @Test
    public void userRepository_addFindExistsDuplicatesGuestsAndAllUsersBranches() {
        UserRepository repo = new UserRepository();

        User guest = new User(UUID.randomUUID());
        User alice = new User(UUID.randomUUID(), "alice", "alice@example.com", "hash", 25);
        User duplicateEmail = new User(UUID.randomUUID(), "alice2", "alice@example.com", "hash", 30);
        User duplicateUsername = new User(UUID.randomUUID(), "alice", "alice2@example.com", "hash", 30);

        repo.add(guest);
        repo.add(alice);

        assertTrue(repo.exists(guest.getId()));
        assertTrue(repo.exists(alice.getId()));
        assertTrue(repo.existsByEmail("alice@example.com"));
        assertTrue(repo.existsByUsername("alice"));
        assertEquals(Optional.of(alice), repo.findByEmail("alice@example.com"));
        assertEquals(Optional.empty(), repo.findByEmail("missing@example.com"));

        assertFalse(repo.existsByEmail(null));
        assertFalse(repo.existsByUsername(null));
        assertEquals(Optional.empty(), repo.findByEmail(null));

        assertThrows(IllegalArgumentException.class, () -> repo.add(duplicateEmail));
        assertThrows(IllegalArgumentException.class, () -> repo.add(duplicateUsername));

        assertTrue(repo.getAllUsers().containsKey(alice.getId()));
        assertThrows(UnsupportedOperationException.class,
                () -> repo.getAllUsers().put(UUID.randomUUID(), alice));
    }

    @Test
    public void userRepository_adminCompanyOwnerPermissionAndCompanyIdsBranches() {
        UserRepository repo = new UserRepository();

        UUID adminId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Admin admin = mock(Admin.class);
        when(admin.getId()).thenReturn(adminId);
        when(admin.getUsername()).thenReturn("admin");

        repo.addAdmin(admin);

        assertTrue(repo.existsAdmin(adminId));
        assertFalse(repo.existsAdmin(UUID.randomUUID()));
        assertTrue(repo.isSystemAdmin("admin"));
        assertFalse(repo.isSystemAdmin(null));
        assertFalse(repo.isSystemAdmin("missing"));

        User founder = new User(adminId, "founder", "founder@example.com", "hash", 40);
        CompanyFounder role = new CompanyFounder("founder@example.com");
        role.getEventsIds().add(eventId);
        founder.getCompanyRoles().put(companyId, role);

        repo.add(founder);

        assertTrue(repo.isSystemAdmin("founder"));
        assertTrue(repo.isSystemAdmin("founder@example.com"));

        assertEquals(List.of(companyId), repo.getCompaniesIdsByMember("founder@example.com"));
        assertTrue(repo.isCompanyOwner("founder@example.com", companyId));
        assertTrue(repo.hasPermission(
                "founder@example.com",
                companyId,
                CompanyPermission.MANAGE_POLICIES,
                eventId
        ));

        assertThrows(IllegalArgumentException.class,
                () -> repo.getCompaniesIdsByMember("missing@example.com"));

        assertThrows(IllegalArgumentException.class,
                () -> repo.isCompanyOwner("missing@example.com", companyId));

        assertThrows(IllegalArgumentException.class,
                () -> repo.hasPermission("missing@example.com", companyId, CompanyPermission.MANAGE_POLICIES, eventId));
    }
}