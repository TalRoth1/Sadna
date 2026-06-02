package org.example.DomainLayer.NotificationAggregate;

import org.example.ApplicationLayer.*;
import org.example.DomainLayer.EventManagementDomainService;
import org.example.DomainLayer.IAdminRepository;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.IPurchaseRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.RolesDomainService;
import org.example.DomainLayer.ActivePurchaseAggregate.ActivePurchase;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.EventAggregate.EventStatus;
import org.example.DomainLayer.Events.PurchaseCompletedEvent;
import org.example.DomainLayer.UserAggregate.CompanyManager;
import org.example.DomainLayer.UserAggregate.CompanyOwner;
import org.example.DomainLayer.UserAggregate.ICompanyMember;
import org.example.DomainLayer.UserAggregate.User;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NotificationTests {
    private static final UUID ADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final String ADMIN_USERNAME = "admin@demo.test";

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static User mockUser(UUID id, String username) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getUsername()).thenReturn(username);
        return user;
    }

    private static AdminService adminService(
            IUserRepository userRepository,
            ICompanyRepository companyRepository,
            IPurchaseRepository purchaseRepository,
            IHistoryRepository historyRepository,
            IAdminRepository adminRepository,
            RolesDomainService rolesDomainService,
            PurchaseDomainService purchaseDomainService,
            QueueManager queueManager,
            INotifier notifier,
            ISystemMetricsTracker iSystemMetricsTracker
    ) {
        return new AdminService(
                userRepository,
                companyRepository,
                purchaseRepository,
                historyRepository,
                adminRepository,
                rolesDomainService,
                purchaseDomainService,
                queueManager,
                notifier,
                iSystemMetricsTracker
        );
    }

    private static void mockValidAdmin(IUserRepository userRepository) {
        when(userRepository.existsAdmin(ADMIN_ID)).thenReturn(true);
        when(userRepository.isSystemAdmin(ADMIN_USERNAME)).thenReturn(true);
    }

    // ---------------------------------------------------------------------
    // 1. System message to all members
    // דרישה: כלל המנויים מקבלים הודעת מערכת ממנהל מערכת
    // ---------------------------------------------------------------------

    @Test
    void sendSystemMessage_notifiesEveryUser() {
        IUserRepository userRepository = mock(IUserRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        IAdminRepository adminRepository = mock(IAdminRepository.class);
        RolesDomainService rolesDomainService = mock(RolesDomainService.class);
        PurchaseDomainService purchaseDomainService = mock(PurchaseDomainService.class);
        QueueManager queueManager = mock(QueueManager.class);
        INotifier notifier = mock(INotifier.class);
        ISystemMetricsTracker iSystemMetricsTracker = mock(ISystemMetricsTracker.class);

        mockValidAdmin(userRepository);

        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();

        User user1 = mockUser(user1Id, "alice");
        User user2 = mockUser(user2Id, "bob");

        Map<UUID, User> users = new LinkedHashMap<>();
        users.put(user1Id, user1);
        users.put(user2Id, user2);

        when(userRepository.getAllUsers()).thenReturn(users);

        AdminService service = adminService(
                userRepository,
                companyRepository,
                purchaseRepository,
                historyRepository,
                adminRepository,
                rolesDomainService,
                purchaseDomainService,
                queueManager,
                notifier,
                iSystemMetricsTracker
        );

        service.sendSystemMessage(
                ADMIN_ID,
                ADMIN_USERNAME,
                "System maintenance tonight"
        );

        verify(notifier).notifyUser(user1Id, "System maintenance tonight");
        verify(notifier).notifyUser(user2Id, "System maintenance tonight");
        verify(adminRepository).saveActionLog(any());
    }

    // ---------------------------------------------------------------------
    // 2. Company closed
    // דרישה: מפיקים / מנהלי אירועים מקבלים הודעה שחברת ההפקה נסגרה
    // כרגע הקוד שולח רק ל-founderUsername.
    // ---------------------------------------------------------------------


    @Test
    void closeCompany_shouldNotifyEveryOwnerAndManagerInCompany_requirement() {
        IUserRepository userRepository = mock(IUserRepository.class);
        ICompanyRepository companyRepository = mock(ICompanyRepository.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        IHistoryRepository historyRepository = mock(IHistoryRepository.class);
        IAdminRepository adminRepository = mock(IAdminRepository.class);
        RolesDomainService rolesDomainService = mock(RolesDomainService.class);
        PurchaseDomainService purchaseDomainService = mock(PurchaseDomainService.class);
        QueueManager queueManager = mock(QueueManager.class);
        INotifier notifier = mock(INotifier.class);
        ISystemMetricsTracker iSystemMetricsTracker = mock(ISystemMetricsTracker.class);

        mockValidAdmin(userRepository);

        UUID companyId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        Company company = mock(Company.class);
        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(company.isActive()).thenReturn(true);
        when(company.getName()).thenReturn("Live Nation Demo");

        User founder = mockUser(founderId, "founder");
        User owner = mockUser(ownerId, "owner");
        User manager = mockUser(managerId, "manager");

        when(founder.getCompanyRole(companyId)).thenReturn(mock(ICompanyMember.class));
        when(owner.getCompanyRole(companyId)).thenReturn(mock(CompanyOwner.class));
        when(manager.getCompanyRole(companyId)).thenReturn(mock(CompanyManager.class));

        when(userRepository.getAllUsers()).thenReturn(Map.of(
                founderId, founder,
                ownerId, owner,
                managerId, manager
        ));

        AdminService service = adminService(
                userRepository,
                companyRepository,
                purchaseRepository,
                historyRepository,
                adminRepository,
                rolesDomainService,
                purchaseDomainService,
                queueManager,
                notifier,
                iSystemMetricsTracker
        );

        service.closeCompany(ADMIN_ID, ADMIN_USERNAME, companyId);

        verify(notifier).notifyUser(eq(founderId), contains("closed"));
        verify(notifier).notifyUser(eq(ownerId), contains("closed"));
        verify(notifier).notifyUser(eq(managerId), contains("closed"));
    }

    // ---------------------------------------------------------------------
    // 3. Event sold out
    // דרישה: מפיק / מנהל אירוע מקבל הודעה כשהאירוע הפך ל-Sold Out
    // ---------------------------------------------------------------------

    @Test
    void completePurchase_whenEventSoldOut_notifiesBuyerAndEventManager() {
        PurchaseDomainService purchaseDomainService = mock(PurchaseDomainService.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);
        QueueManager queueManager = mock(QueueManager.class);
        INotifier notifier = mock(INotifier.class);

        PurchaseService service = new PurchaseService(
                purchaseDomainService,
                eventPublisher,
                queueManager,
                notifier
        );

        UUID activePurchaseId = UUID.randomUUID();
        String managerIdentifier = "manager@demo.test";
        UUID buyerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        ActivePurchase activePurchase = mock(ActivePurchase.class);
        PaymentDetails paymentDetails = mock(PaymentDetails.class);

        when(activePurchase.getUserID()).thenReturn(buyerId);
        when(activePurchase.getEventID()).thenReturn(eventId);

        when(purchaseDomainService.viewActivePurchase(activePurchaseId))
                .thenReturn(activePurchase);

        when(purchaseDomainService.completePurchase(any(), any(), any()))
                .thenReturn(true);

        when(purchaseDomainService.getEventManager(eventId))
                .thenReturn(managerIdentifier);

        service.completePurchase(activePurchaseId, paymentDetails, null);

        verify(notifier).notifyUser(buyerId, "Purchase Complete");
        verify(notifier).notifyUser(
                eq(managerIdentifier),
                eq("Tickets to event: " + eventId + " have been SOLD OUT")
        );

        verify(eventPublisher).publish(any(PurchaseCompletedEvent.class));
    }

    // ---------------------------------------------------------------------
    // 4. Event changed / date changed
    // דרישה: רוכשים מקבלים הודעה על שינוי מועד / שינוי אירוע
    // EventService.editEvent כבר שולח הודעה לכל participants.
    // ---------------------------------------------------------------------

    @Test
    void editEvent_notifiesAllParticipants() {
        EventManagementDomainService eventManagementDomainService =
                mock(EventManagementDomainService.class);
        INotifier notifier = mock(INotifier.class);

        EventService service = new EventService(
                eventManagementDomainService,
                notifier
        );

        UUID eventId = UUID.randomUUID();
        UUID buyer1 = UUID.randomUUID();
        UUID buyer2 = UUID.randomUUID();

        LocalDateTime newDate = LocalDateTime.now().plusDays(10);

        when(eventManagementDomainService.editEvent(
                eq(eventId),
                eq("Coldplay changed"),
                eq(newDate),
                eq("Tel Aviv"),
                eq("Coldplay"),
                eq("Concert"),
                eq(EventStatus.ACTIVE),
                eq("hello")
        )).thenReturn(Set.of(buyer1, buyer2));

        /*
         * editEvent sends notifications before it maps the event back to DTO.
         * We intentionally throw here to avoid needing a full Event aggregate
         * just for this unit test.
         */
        when(eventManagementDomainService.getEventForView(eventId))
                .thenThrow(new RuntimeException("stop after notifications"));

        assertThrows(RuntimeException.class, () ->
                service.editEvent(
                        eventId,
                        "Coldplay changed",
                        newDate,
                        "Tel Aviv",
                        "Coldplay",
                        "Concert",
                        EventStatus.ACTIVE, "hello"
                )
        );

        verify(notifier).notifyUser(
                buyer1,
                "Event: " + eventId + " has been changed"
        );

        verify(notifier).notifyUser(
                buyer2,
                "Event: " + eventId + " has been changed"
        );
    }

    // ---------------------------------------------------------------------
    // 6. Role / permission changed
    // דרישה: מנהל אירוע / מפיק מקבל הודעה כשהתפקיד או ההרשאות שלו משתנות
    // לא ראינו חיבור Notifier ברור ב-RolesDomainService.
    // ---------------------------------------------------------------------

    @Test
    void changeManagerPermissions_shouldNotifyManager_requirement() {
        // Arrange
        RolesDomainService rolesDomainService = mock(RolesDomainService.class);
        PurchaseDomainService purchaseDomainService = mock(PurchaseDomainService.class);
        INotifier notifier = mock(INotifier.class);

        CompanyService companyService = new CompanyService(
                rolesDomainService,
                purchaseDomainService,
                notifier,
                mock(EventService.class)
        );

        String ownerUsername = "owner_bob";
        UUID companyId = UUID.randomUUID();
        String managerUsername = "manager_alice";
        Set<CompanyPermission> newPermissions =
                Set.of(CompanyPermission.MANAGE_INVENTORY);

        // Act
        companyService.changeManagerPermissions(
                ownerUsername,
                companyId,
                managerUsername,
                newPermissions
        );

        // Assert
        verify(notifier, times(1)).notifyUser(
                managerUsername,
                "Your manager permissions have changed."
        );
    }
    // ---------------------------------------------------------------------
    // 7. Producer / company message
    // דרישה: כלל המנויים או רוכשים רלוונטיים מקבלים הודעה ממפיק / חברת הפקה
    // לא ראינו feature/endpoint כזה בקוד.
    // ---------------------------------------------------------------------

    @Test
    void producerMessage_shouldNotifyRelevantBuyers_requirement() {
        PurchaseDomainService purchaseDomainService = mock(PurchaseDomainService.class);
        EventPublisher eventPublisher = mock(EventPublisher.class);
        QueueManager queueManager = mock(QueueManager.class);
        INotifier notifier = mock(INotifier.class);

        PurchaseService service = new PurchaseService(
                purchaseDomainService,
                eventPublisher,
                queueManager,
                notifier
        );

        UUID eventId = UUID.randomUUID();
        UUID buyer1 = UUID.randomUUID();
        UUID buyer2 = UUID.randomUUID();

        PurchaseHistory h1 = mock(PurchaseHistory.class);
        PurchaseHistory h2 = mock(PurchaseHistory.class);

        when(h1.getUserId()).thenReturn(buyer1);
        when(h2.getUserId()).thenReturn(buyer2);

        when(purchaseDomainService.getHistoryByEvent(eventId))
                .thenReturn(List.of(h1, h2));

        service.sendProducerMessageToEventBuyers(
                eventId,
                "Gates open at 18:00"
        );

        verify(notifier).notifyUser(buyer1, "Gates open at 18:00");
        verify(notifier).notifyUser(buyer2, "Gates open at 18:00");
    }

    // ---------------------------------------------------------------------
    // 8. Active purchase expiring
    // דרישה: רוכש מקבל אזהרה כשהזמנה פעילה עומדת לפוג
    //
    // כרגע ActivePurchaseCleaner רץ בלולאה אינסופית ושולח לפי lastUpdate <= 1,
    // ולכן קשה לבדוק אותו בצורה נקייה וגם ראינו שהוא עלול לעשות spam.
    // צריך להוציא מתודה sweepOnce(now), ואז להפעיל את הטסט הזה.
    // ---------------------------------------------------------------------

    @Test
    void activePurchaseCleaner_shouldNotifyOnceWhenPurchaseIsAboutToExpire_requirement() {
        PurchaseService purchaseService = mock(PurchaseService.class);
        IPurchaseRepository purchaseRepository = mock(IPurchaseRepository.class);
        INotifier notifier = mock(INotifier.class);

        UUID buyerId = UUID.randomUUID();
        UUID activePurchaseId = UUID.randomUUID();

        ActivePurchase purchase = mock(ActivePurchase.class);

        when(purchase.getActivePurchaseId()).thenReturn(activePurchaseId);
        when(purchase.getUserID()).thenReturn(buyerId);
        when(purchase.getEndTime()).thenReturn(LocalDateTime.now().plusSeconds(45));
        when(purchase.isExpired(any(LocalDateTime.class))).thenReturn(false);

        when(purchaseRepository.findAll()).thenReturn(java.util.List.of(purchase));

        ActivePurchaseCleaner cleaner =
                new ActivePurchaseCleaner(purchaseService, purchaseRepository, notifier);

        /*
         * Desired after refactor:
         *
         * cleaner.sweepOnce(LocalDateTime.now());
         * cleaner.sweepOnce(LocalDateTime.now().plusSeconds(5));
         *
         * verify(notifier, times(1)).notifyUser(
         *      buyerId,
         *      "Active Order is about to be canceled"
         * );
         */
    }

    // ---------------------------------------------------------------------
    // 9. Delayed notification
    // דרישה: משתמש לא מחובר מקבל unread notification אחרי login
    //
    // את זה עדיף לבדוק באינטגרציה מול NotificationRepository/Notifier,
    // כי NotificationService עצמו רק קורא notifier.notifyUser ואז שולף unread.
    // ---------------------------------------------------------------------

    @Test
    void delayedNotification_shouldBeReturnedAsUnreadAfterUserLogsIn_requirement() {
        /*
         * Desired integration scenario:
         *
         * 1. user is registered but has no active SSE connection.
         * 2. admin sends system message.
         * 3. NotificationRepository stores unread notification.
         * 4. user logs in.
         * 5. GET /api/notifications/users/{userId}/unread returns the message.
         */
    }
}
