package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.AdminDTOs.AdminAnalyticsDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminCompanyDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminComplaintDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminQueueSettingsRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminQueueSnapshotDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminSubscriberDTO;
import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;
import org.example.DomainLayer.IAdminRepository;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.IPurchaseRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.RolesDomainService;
import org.example.DomainLayer.AdminAggregate.AdminActionLog;
import org.example.DomainLayer.AdminAggregate.AdminComplaint;
import org.example.DomainLayer.AdminAggregate.SystemAnalyticsSnapshot;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.UserAggregate.CompanyFounder;
import org.example.DomainLayer.UserAggregate.CompanyManager;
import org.example.DomainLayer.UserAggregate.CompanyOwner;
import org.example.DomainLayer.UserAggregate.ICompanyMember;
import org.example.DomainLayer.UserAggregate.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminService}.
 *
 * All dependencies are mocked. The shared {@code @BeforeEach} stubs a valid
 * admin identity so happy-path tests stay concise; guard-failure tests either
 * use different IDs or rely on the guard throwing before those stubs are
 * reached. Shared stubs are declared {@code lenient()} to avoid Mockito's
 * "unnecessary stubbing" strict-mode failures in tests that never reach the
 * admin-validation check.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @Mock private IUserRepository     userRepository;
    @Mock private ICompanyRepository  companyRepository;
    @Mock private IPurchaseRepository purchaseRepository;
    @Mock private IHistoryRepository  historyRepository;
    @Mock private IAdminRepository    adminRepository;
    @Mock private RolesDomainService  rolesDomainService;
    @Mock private PurchaseDomainService purchaseDomainService;
    @Mock private QueueManager        queueManager;
    @Mock private INotifier           notifier;

    @InjectMocks
    private AdminService adminService;

    // ── Shared test constants ─────────────────────────────────────────────────

    private static final UUID   ADMIN_ID       = UUID.randomUUID();
    private static final String ADMIN_USERNAME = "sysadmin";

    // ── Shared admin-guard stub ───────────────────────────────────────────────
    //
    // Declared lenient so that tests which throw *before* reaching the guard
    // (e.g. null-ID checks) are not penalised for "unused stubbing".

    @BeforeEach
    void stubValidAdmin() {
        lenient().when(userRepository.existsAdmin(ADMIN_ID)).thenReturn(true);
        lenient().when(userRepository.isSystemAdmin(ADMIN_USERNAME)).thenReturn(true);
    }

    // =========================================================================
    // A. Authorization Guard Tests
    // =========================================================================

    @Nested
    class AuthorizationGuardTests {

        @Test
        void anyAdminMethod_NullAdminId_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.getCompanies(null, ADMIN_USERNAME));
        }

        @Test
        void anyAdminMethod_NullAdminUsername_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.getCompanies(ADMIN_ID, null));
        }

        @Test
        void anyAdminMethod_BlankAdminUsername_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.getCompanies(ADMIN_ID, "   "));
        }

        @Test
        void anyAdminMethod_AdminIdNotFoundInRepository_ThrowsIllegalArgumentException() {
            // Use a fresh UUID so the @BeforeEach stub for ADMIN_ID does not interfere.
            UUID unknownId = UUID.randomUUID();
            when(userRepository.existsAdmin(unknownId)).thenReturn(false);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> adminService.getCompanies(unknownId, ADMIN_USERNAME));

            assertEquals("User is not system admin", ex.getMessage());
        }

        @Test
        void anyAdminMethod_AdminUsernameNotFoundInRepository_ThrowsIllegalArgumentException() {
            UUID adminId = UUID.randomUUID();
            when(userRepository.existsAdmin(adminId)).thenReturn(true);
            when(userRepository.isSystemAdmin("notAdmin")).thenReturn(false);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> adminService.getCompanies(adminId, "notAdmin"));

            assertEquals("User is not system admin", ex.getMessage());
        }

        @Test
        void anyAdminMethod_ValidAdminCredentials_ProceedsWithoutThrowingAuthError() {
            when(companyRepository.getAll()).thenReturn(List.of());
            when(userRepository.getAllUsers()).thenReturn(Map.of());

            assertDoesNotThrow(() -> adminService.getCompanies(ADMIN_ID, ADMIN_USERNAME));
        }
    }

    // =========================================================================
    // B. closeCompany Tests
    // =========================================================================

    @Nested
    class CloseCompanyTests {

        @Test
        void closeCompany_NullCompanyId_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.closeCompany(ADMIN_ID, ADMIN_USERNAME, null));
        }

        @Test
        void closeCompany_CompanyNotFound_ThrowsIllegalArgumentException() {
            UUID companyId = UUID.randomUUID();
            when(companyRepository.findByID(companyId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> adminService.closeCompany(ADMIN_ID, ADMIN_USERNAME, companyId));
        }

        @Test
        void closeCompany_CompanyAlreadyClosed_ThrowsIllegalStateException() {
            UUID companyId = UUID.randomUUID();
            Company company = mock(Company.class);
            when(company.isActive()).thenReturn(false);
            when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));

            assertThrows(IllegalStateException.class,
                    () -> adminService.closeCompany(ADMIN_ID, ADMIN_USERNAME, companyId));
        }

        @Test
        void closeCompany_HappyPath_DelegatesToRolesServiceNotifiesStaffAndLogsAction() {
            UUID companyId = UUID.randomUUID();
            Company company = mock(Company.class);
            when(company.isActive()).thenReturn(true);
            when(company.getName()).thenReturn("Acme Corp");
            when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));

            UUID staffId = UUID.randomUUID();
            User staffMember = mock(User.class);
            when(staffMember.getId()).thenReturn(staffId);
            when(staffMember.getCompanyRole(companyId)).thenReturn(mock(ICompanyMember.class));

            when(userRepository.getAllUsers()).thenReturn(Map.of(staffId, staffMember));

            adminService.closeCompany(ADMIN_ID, ADMIN_USERNAME, companyId);

            verify(rolesDomainService).closeCompanyAsAdmin(ADMIN_USERNAME, companyId);
            verify(notifier).notifyUser(eq(staffId), anyString());
            verify(adminRepository).saveActionLog(any(AdminActionLog.class));
        }

        @Test
        void closeCompany_HappyPath_DoesNotNotifyUsersWithNoRoleInTheClosedCompany() {
            UUID companyId = UUID.randomUUID();
            Company company = mock(Company.class);
            when(company.isActive()).thenReturn(true);
            when(company.getName()).thenReturn("Acme Corp");
            when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));

            UUID unrelatedId = UUID.randomUUID();
            User unrelatedUser = mock(User.class);
            when(unrelatedUser.getCompanyRole(companyId)).thenReturn(null);

            when(userRepository.getAllUsers()).thenReturn(Map.of(unrelatedId, unrelatedUser));

            adminService.closeCompany(ADMIN_ID, ADMIN_USERNAME, companyId);

            verify(notifier, never()).notifyUser(any(UUID.class), anyString());
        }
    }

    // =========================================================================
    // C. removeSubscriber Tests
    // =========================================================================

    @Nested
    class RemoveSubscriberTests {

        @Test
        void removeSubscriber_NullUsername_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.removeSubscriber(ADMIN_ID, ADMIN_USERNAME, null));
        }

        @Test
        void removeSubscriber_BlankUsername_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.removeSubscriber(ADMIN_ID, ADMIN_USERNAME, "   "));
        }

        @Test
        void removeSubscriber_TargetIsSystemAdmin_ThrowsIllegalArgumentException() {
            when(userRepository.isSystemAdmin("anotherAdmin")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> adminService.removeSubscriber(ADMIN_ID, ADMIN_USERNAME, "anotherAdmin"));

            assertEquals("Cannot remove system admin", ex.getMessage());
        }

        @Test
        void removeSubscriber_SubscriberNotFound_ThrowsIllegalArgumentException() {
            when(userRepository.isSystemAdmin("ghost")).thenReturn(false);
            when(userRepository.getAllUsers()).thenReturn(Map.of());

            assertThrows(IllegalArgumentException.class,
                    () -> adminService.removeSubscriber(ADMIN_ID, ADMIN_USERNAME, "ghost"));
        }

        @Test
        void removeSubscriber_HappyPath_RemovesUserNotifiesAndLogsAction() {
            String targetUsername = "targetUser";
            UUID   targetId       = UUID.randomUUID();
            User   targetUser     = mock(User.class);
            when(targetUser.getUsername()).thenReturn(targetUsername);
            when(targetUser.getId()).thenReturn(targetId);

            when(userRepository.isSystemAdmin(targetUsername)).thenReturn(false);
            when(userRepository.getAllUsers()).thenReturn(Map.of(targetId, targetUser));

            adminService.removeSubscriber(ADMIN_ID, ADMIN_USERNAME, targetUsername);

            verify(targetUser).removeFromPlatformAsAdmin();
            verify(notifier).notifyUser(eq(targetId), anyString());
            verify(adminRepository).saveActionLog(any(AdminActionLog.class));
        }
    }

    // =========================================================================
    // D. sendSystemMessage Tests
    // =========================================================================

    @Nested
    class SendSystemMessageTests {

        @Test
        void sendSystemMessage_NullMessage_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.sendSystemMessage(ADMIN_ID, ADMIN_USERNAME, null));
        }

        @Test
        void sendSystemMessage_BlankMessage_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.sendSystemMessage(ADMIN_ID, ADMIN_USERNAME, "   "));
        }

        @Test
        void sendSystemMessage_HappyPath_NotifiesEveryUserAndLogsAction() {
            UUID user1Id = UUID.randomUUID();
            UUID user2Id = UUID.randomUUID();
            User user1   = mock(User.class);
            User user2   = mock(User.class);
            when(user1.getId()).thenReturn(user1Id);
            when(user2.getId()).thenReturn(user2Id);
            when(userRepository.getAllUsers())
                    .thenReturn(Map.of(user1Id, user1, user2Id, user2));

            adminService.sendSystemMessage(ADMIN_ID, ADMIN_USERNAME, "Platform maintenance at midnight.");

            verify(notifier).notifyUser(eq(user1Id), anyString());
            verify(notifier).notifyUser(eq(user2Id), anyString());
            verify(adminRepository).saveActionLog(any(AdminActionLog.class));
        }

        @Test
        void sendSystemMessage_EmptyUserRepository_LogsActionWithoutSendingAnyNotification() {
            when(userRepository.getAllUsers()).thenReturn(Map.of());

            adminService.sendSystemMessage(ADMIN_ID, ADMIN_USERNAME, "Hello, empty world.");

            verifyNoInteractions(notifier);
            verify(adminRepository).saveActionLog(any(AdminActionLog.class));
        }
    }

    // =========================================================================
    // E. getCompanies Tests
    // =========================================================================

    @Nested
    class GetCompaniesTests {

        @Test
        void getCompanies_HappyPath_ReturnsDTOForEachCompany() {
            Company c1 = new Company("founder1@test.com", "Acme");
            Company c2 = new Company("founder2@test.com", "Globex");
            when(companyRepository.getAll()).thenReturn(List.of(c1, c2));
            when(userRepository.getAllUsers()).thenReturn(Map.of());

            List<AdminCompanyDTO> result = adminService.getCompanies(ADMIN_ID, ADMIN_USERNAME);

            assertEquals(2, result.size());
        }

        @Test
        void getCompanies_CorrectlyCountsFoundersAndOwnersAsOwnersAndManagers() {
            Company company   = new Company("founder@test.com", "Acme");
            UUID    companyId = company.getId();

            User founderUser  = mock(User.class);
            User ownerUser    = mock(User.class);
            User managerUser  = mock(User.class);
            User strangerUser = mock(User.class);

            when(founderUser.getCompanyRole(companyId))
                    .thenReturn(new CompanyFounder("founderUser"));
            when(ownerUser.getCompanyRole(companyId))
                    .thenReturn(new CompanyOwner("ownerUser", null));
            when(managerUser.getCompanyRole(companyId))
                    .thenReturn(new CompanyManager("managerUser", null, Set.of()));
            when(strangerUser.getCompanyRole(companyId))
                    .thenReturn(null);

            when(companyRepository.getAll()).thenReturn(List.of(company));
            when(userRepository.getAllUsers()).thenReturn(Map.of(
                    UUID.randomUUID(), founderUser,
                    UUID.randomUUID(), ownerUser,
                    UUID.randomUUID(), managerUser,
                    UUID.randomUUID(), strangerUser
            ));

            AdminCompanyDTO dto = adminService.getCompanies(ADMIN_ID, ADMIN_USERNAME).get(0);

            // Founder + Owner both count as owners
            assertEquals(2, dto.ownersCount);
            assertEquals(1, dto.managersCount);
        }

        @Test
        void getCompanies_ActiveCompany_DtoHasCorrectNameAndActiveStatus() {
            Company company = new Company("a@test.com", "Active Inc");
            when(companyRepository.getAll()).thenReturn(List.of(company));
            when(userRepository.getAllUsers()).thenReturn(Map.of());

            AdminCompanyDTO dto = adminService.getCompanies(ADMIN_ID, ADMIN_USERNAME).get(0);

            assertEquals("Active Inc", dto.name);
            assertEquals("active", dto.status);
        }
    }

    // =========================================================================
    // F. getSubscribers Tests
    // =========================================================================

    @Nested
    class GetSubscribersTests {

        @Test
        void getSubscribers_FiltersOutSystemAdmins() {
            UUID adminUserId = UUID.randomUUID();
            User adminUser   = mock(User.class);
            when(adminUser.getUsername()).thenReturn("sysadmin2");
            when(userRepository.isSystemAdmin("sysadmin2")).thenReturn(true);
            when(userRepository.getAllUsers()).thenReturn(Map.of(adminUserId, adminUser));

            List<AdminSubscriberDTO> result = adminService.getSubscribers(ADMIN_ID, ADMIN_USERNAME);

            assertTrue(result.isEmpty());
        }

        @Test
        void getSubscribers_FiltersOutRemovedUsers() {
            UUID removedId   = UUID.randomUUID();
            // Real User object so we can actually call removeFromPlatformAsAdmin().
            User removedUser = new User(removedId, "removed", "r@test.com", "hash", 25f);
            removedUser.removeFromPlatformAsAdmin();

            when(userRepository.isSystemAdmin("removed")).thenReturn(false);
            when(userRepository.getAllUsers()).thenReturn(Map.of(removedId, removedUser));

            List<AdminSubscriberDTO> result = adminService.getSubscribers(ADMIN_ID, ADMIN_USERNAME);

            assertTrue(result.isEmpty());
        }

        @Test
        void getSubscribers_HappyPath_ReturnsCorrectDTOFieldsForEligibleUser() {
            UUID userId = UUID.randomUUID();
            User user   = new User(userId, "john", "john@test.com", "hash", 30f);

            when(userRepository.isSystemAdmin("john")).thenReturn(false);
            when(userRepository.getAllUsers()).thenReturn(Map.of(userId, user));

            List<AdminSubscriberDTO> result = adminService.getSubscribers(ADMIN_ID, ADMIN_USERNAME);

            assertEquals(1, result.size());
            AdminSubscriberDTO dto = result.get(0);
            assertEquals(userId,          dto.id);
            assertEquals("john",          dto.username);
            assertEquals("john@test.com", dto.email);
            assertNotNull(dto.status);
            assertNotNull(dto.role);
        }
    }

    // =========================================================================
    // G. getPurchaseHistoryByFilter Tests
    // =========================================================================

    @Nested
    class PurchaseHistoryFilterTests {

        @Test
        void getPurchaseHistoryByFilter_NullFilterType_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.getPurchaseHistoryByFilter(
                            ADMIN_ID, ADMIN_USERNAME, null, null));
        }

        @Test
        void getPurchaseHistoryByFilter_BlankFilterType_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.getPurchaseHistoryByFilter(
                            ADMIN_ID, ADMIN_USERNAME, "   ", null));
        }

        @Test
        void getPurchaseHistoryByFilter_InvalidFilterType_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.getPurchaseHistoryByFilter(
                            ADMIN_ID, ADMIN_USERNAME, "store", null));
        }

        @Test
        void getPurchaseHistoryByFilter_FilterTypeAll_DelegatesToGetAllPurchaseHistory() {
            when(purchaseDomainService.getAllHistory()).thenReturn(List.of());

            List<PurchaseHistoryDTO> result = adminService.getPurchaseHistoryByFilter(
                    ADMIN_ID, ADMIN_USERNAME, "all", null);

            verify(purchaseDomainService).getAllHistory();
            assertNotNull(result);
        }

        @Test
        void getPurchaseHistoryByFilter_FilterTypeUser_NullFilterId_ThrowsIllegalArgumentException() {
            // Null filterId propagates as "User ID is required" from getPurchaseHistoryByUser.
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.getPurchaseHistoryByFilter(
                            ADMIN_ID, ADMIN_USERNAME, "user", null));
        }

        @Test
        void getPurchaseHistoryByFilter_FilterTypeEvent_DelegatesToGetHistoryByEvent() {
            UUID eventId = UUID.randomUUID();
            when(purchaseDomainService.getHistoryByEvent(eventId)).thenReturn(List.of());

            adminService.getPurchaseHistoryByFilter(ADMIN_ID, ADMIN_USERNAME, "event", eventId);

            verify(purchaseDomainService).getHistoryByEvent(eventId);
        }

        @Test
        void getPurchaseHistoryByFilter_FilterTypeCompany_DelegatesToGetHistoryByCompany() {
            UUID companyId = UUID.randomUUID();
            when(purchaseDomainService.getHistoryByCompany(companyId)).thenReturn(List.of());

            adminService.getPurchaseHistoryByFilter(ADMIN_ID, ADMIN_USERNAME, "company", companyId);

            verify(purchaseDomainService).getHistoryByCompany(companyId);
        }

        @Test
        void getPurchaseHistoryByFilter_FilterTypeIsUpperCase_RoutesCorrectly() {
            // The switch uses filterType.toLowerCase(), so "ALL" must behave as "all".
            when(purchaseDomainService.getAllHistory()).thenReturn(List.of());

            assertDoesNotThrow(() -> adminService.getPurchaseHistoryByFilter(
                    ADMIN_ID, ADMIN_USERNAME, "ALL", null));
        }
    }

    // =========================================================================
    // H. createComplaint Tests
    // =========================================================================

    @Nested
    class CreateComplaintTests {

        @Test
        void createComplaint_NullReporterUserId_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.createComplaint(null, "user1", "Title", "Description"));
        }

        @Test
        void createComplaint_NullReporterUsername_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.createComplaint(UUID.randomUUID(), null, "Title", "Description"));
        }

        @Test
        void createComplaint_BlankReporterUsername_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.createComplaint(UUID.randomUUID(), "  ", "Title", "Description"));
        }

        @Test
        void createComplaint_NullTitle_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.createComplaint(UUID.randomUUID(), "user1", null, "Description"));
        }

        @Test
        void createComplaint_NullDescription_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.createComplaint(UUID.randomUUID(), "user1", "Title", null));
        }

        @Test
        void createComplaint_HappyPath_PersistsAndReturnsDTOWithOpenStatus() {
            UUID reporterId = UUID.randomUUID();

            AdminComplaintDTO result = adminService.createComplaint(
                    reporterId, "user1", "Bug report", "Something is broken");

            verify(adminRepository).saveComplaint(any(AdminComplaint.class));
            assertNotNull(result.id);
            assertEquals(reporterId, result.reporterUserId);
            assertEquals("user1",        result.reporterUsername);
            assertEquals("Bug report",   result.title);
            assertEquals("OPEN",         result.status);
        }

        @Test
        void createComplaint_DoesNotPerformAdminValidation() {
            // createComplaint is the one public method that any logged-in user can call.
            adminService.createComplaint(UUID.randomUUID(), "anyUser", "Title", "Desc");

            verify(userRepository, never()).existsAdmin(any());
        }
    }

    // =========================================================================
    // I. getAllComplaints & getOpenComplaints Tests
    // =========================================================================

    @Nested
    class GetComplaintsTests {

        @Test
        void getAllComplaints_HappyPath_ReturnsAllStatusesIncluded() {
            AdminComplaint open     = new AdminComplaint(UUID.randomUUID(), "u1", "T1", "D1");
            AdminComplaint answered = new AdminComplaint(UUID.randomUUID(), "u2", "T2", "D2");
            answered.respond("admin", "Your issue is resolved.");
            AdminComplaint closed   = new AdminComplaint(UUID.randomUUID(), "u3", "T3", "D3");
            closed.close();

            when(adminRepository.getAllComplaints()).thenReturn(List.of(open, answered, closed));

            List<AdminComplaintDTO> result = adminService.getAllComplaints(ADMIN_ID, ADMIN_USERNAME);

            assertEquals(3, result.size());
        }

        @Test
        void getOpenComplaints_HappyPath_ReturnsOnlyOpenComplaints() {
            AdminComplaint openComplaint = new AdminComplaint(UUID.randomUUID(), "u1", "T1", "D1");
            when(adminRepository.getOpenComplaints()).thenReturn(List.of(openComplaint));

            List<AdminComplaintDTO> result = adminService.getOpenComplaints(ADMIN_ID, ADMIN_USERNAME);

            assertEquals(1, result.size());
            assertEquals("OPEN", result.get(0).status);
        }

        @Test
        void getAllComplaints_EmptyRepository_ReturnsEmptyList() {
            when(adminRepository.getAllComplaints()).thenReturn(List.of());

            List<AdminComplaintDTO> result = adminService.getAllComplaints(ADMIN_ID, ADMIN_USERNAME);

            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // J. respondToComplaint Tests
    // =========================================================================

    @Nested
    class RespondToComplaintTests {

        @Test
        void respondToComplaint_NullComplaintId_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.respondToComplaint(ADMIN_ID, ADMIN_USERNAME, null, "response"));
        }

        @Test
        void respondToComplaint_ComplaintNotFound_ThrowsIllegalArgumentException() {
            UUID complaintId = UUID.randomUUID();
            when(adminRepository.findComplaintById(complaintId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> adminService.respondToComplaint(
                            ADMIN_ID, ADMIN_USERNAME, complaintId, "response"));
        }

        @Test
        void respondToComplaint_HappyPath_UpdatesStatusNotifiesReporterAndLogsAction() {
            UUID reporterId = UUID.randomUUID();
            AdminComplaint complaint = new AdminComplaint(reporterId, "reporter", "Title", "Desc");
            UUID complaintId = complaint.getId();

            when(adminRepository.findComplaintById(complaintId)).thenReturn(Optional.of(complaint));

            AdminComplaintDTO result = adminService.respondToComplaint(
                    ADMIN_ID, ADMIN_USERNAME, complaintId, "Here is your answer.");

            verify(adminRepository).saveComplaint(complaint);
            verify(notifier).notifyUser(eq(reporterId), anyString());
            verify(adminRepository).saveActionLog(any(AdminActionLog.class));
            assertEquals("ANSWERED",          result.status);
            assertEquals("Here is your answer.", result.adminResponse);
        }

        @Test
        void respondToComplaint_BlankResponseText_ThrowsIllegalArgumentException() {
            UUID reporterId  = UUID.randomUUID();
            AdminComplaint complaint = new AdminComplaint(reporterId, "reporter", "Title", "Desc");
            UUID complaintId = complaint.getId();

            when(adminRepository.findComplaintById(complaintId)).thenReturn(Optional.of(complaint));

            // AdminComplaint.respond() validates the response text.
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.respondToComplaint(
                            ADMIN_ID, ADMIN_USERNAME, complaintId, "   "));
        }
    }

    // =========================================================================
    // K. closeComplaint Tests
    // =========================================================================

    @Nested
    class CloseComplaintTests {

        @Test
        void closeComplaint_NullComplaintId_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.closeComplaint(ADMIN_ID, ADMIN_USERNAME, null));
        }

        @Test
        void closeComplaint_ComplaintNotFound_ThrowsIllegalArgumentException() {
            UUID complaintId = UUID.randomUUID();
            when(adminRepository.findComplaintById(complaintId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> adminService.closeComplaint(ADMIN_ID, ADMIN_USERNAME, complaintId));
        }

        @Test
        void closeComplaint_HappyPath_SetsStatusToClosedAndLogsAction() {
            AdminComplaint complaint = new AdminComplaint(UUID.randomUUID(), "reporter", "Title", "Desc");
            UUID complaintId = complaint.getId();

            when(adminRepository.findComplaintById(complaintId)).thenReturn(Optional.of(complaint));

            AdminComplaintDTO result = adminService.closeComplaint(ADMIN_ID, ADMIN_USERNAME, complaintId);

            verify(adminRepository).saveComplaint(complaint);
            verify(adminRepository).saveActionLog(any(AdminActionLog.class));
            assertEquals("CLOSED", result.status);
        }

        @Test
        void closeComplaint_HappyPath_DoesNotSendAnyNotificationToReporter() {
            AdminComplaint complaint = new AdminComplaint(UUID.randomUUID(), "reporter", "Title", "Desc");
            UUID complaintId = complaint.getId();

            when(adminRepository.findComplaintById(complaintId)).thenReturn(Optional.of(complaint));

            adminService.closeComplaint(ADMIN_ID, ADMIN_USERNAME, complaintId);

            // Unlike respondToComplaint, closing a complaint produces no notification.
            verifyNoInteractions(notifier);
        }
    }

    // =========================================================================
    // L. Queue Management Tests
    // =========================================================================

    @Nested
    class QueueManagementTests {

        /** Convenience factory so snapshot construction is not repeated everywhere. */
        private QueueManager.QueueSnapshot makeSnapshot(UUID eventId) {
            return new QueueManager.QueueSnapshot(eventId, 5, 2, 10, 10);
        }

        @Test
        void getAllQueues_HappyPath_ReturnsDTOForEachActiveQueue() {
            UUID e1 = UUID.randomUUID();
            UUID e2 = UUID.randomUUID();
            when(queueManager.getAllQueueSnapshots())
                    .thenReturn(List.of(makeSnapshot(e1), makeSnapshot(e2)));

            List<AdminQueueSnapshotDTO> result = adminService.getAllQueues(ADMIN_ID, ADMIN_USERNAME);

            assertEquals(2, result.size());
        }

        @Test
        void getQueue_NullEventId_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.getQueue(ADMIN_ID, ADMIN_USERNAME, null));
        }

        @Test
        void getQueue_HappyPath_ReturnsMappedSnapshotFields() {
            UUID eventId = UUID.randomUUID();
            when(queueManager.getQueueSnapshot(eventId)).thenReturn(makeSnapshot(eventId));

            AdminQueueSnapshotDTO result = adminService.getQueue(ADMIN_ID, ADMIN_USERNAME, eventId);

            assertEquals(eventId, result.eventId);
            assertEquals(5,       result.queueSize);
            assertEquals(2,       result.activeSelectorsCount);
            assertEquals(10,      result.maxConcurrentSelectors);
            assertEquals(10,      result.minutesToStartSelection);
        }

        @Test
        void releaseQueueBatch_NullEventId_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.releaseQueueBatch(ADMIN_ID, ADMIN_USERNAME, null, 5));
        }

        @Test
        void releaseQueueBatch_ZeroBatchSize_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.releaseQueueBatch(
                            ADMIN_ID, ADMIN_USERNAME, UUID.randomUUID(), 0));
        }

        @Test
        void releaseQueueBatch_NegativeBatchSize_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.releaseQueueBatch(
                            ADMIN_ID, ADMIN_USERNAME, UUID.randomUUID(), -3));
        }

        @Test
        void releaseQueueBatch_HappyPath_DelegatesReleaseBatchLogsAndReturnsSnapshot() {
            UUID eventId = UUID.randomUUID();
            when(queueManager.getQueueSnapshot(eventId)).thenReturn(makeSnapshot(eventId));

            AdminQueueSnapshotDTO result =
                    adminService.releaseQueueBatch(ADMIN_ID, ADMIN_USERNAME, eventId, 5);

            verify(queueManager).releaseBatch(eventId, 5);
            verify(adminRepository).saveActionLog(any(AdminActionLog.class));
            assertNotNull(result);
        }

        @Test
        void clearQueue_NullEventId_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.clearQueue(ADMIN_ID, ADMIN_USERNAME, null));
        }

        @Test
        void clearQueue_HappyPath_ClearsQueueLogsActionAndReturnsSnapshot() {
            UUID eventId = UUID.randomUUID();
            when(queueManager.getQueueSnapshot(eventId)).thenReturn(makeSnapshot(eventId));

            AdminQueueSnapshotDTO result = adminService.clearQueue(ADMIN_ID, ADMIN_USERNAME, eventId);

            verify(queueManager).clearQueue(eventId);
            verify(adminRepository).saveActionLog(any(AdminActionLog.class));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // M. updateQueueSettings Tests
    // =========================================================================

    @Nested
    class UpdateQueueSettingsTests {

        /**
         * updateQueueSettings ends by calling getAnalytics(), which in turn reads
         * from all five data-source dependencies. This helper stubs them all so the
         * happy-path tests don't repeat the same boilerplate.
         */
        private void stubAnalyticsDependencies() {
            when(userRepository.getAllUsers()).thenReturn(Map.of());
            when(companyRepository.getAllActive()).thenReturn(List.of());
            when(queueManager.getAllQueueSnapshots()).thenReturn(List.of());
            when(purchaseRepository.findAll()).thenReturn(List.of());
            when(historyRepository.getAll()).thenReturn(List.of());
        }

        @Test
        void updateQueueSettings_NullRequest_ThrowsIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> adminService.updateQueueSettings(ADMIN_ID, ADMIN_USERNAME, null));
        }

        @Test
        void updateQueueSettings_ZeroMaxConcurrentSelectors_ThrowsIllegalArgumentException() {
            AdminQueueSettingsRequest request = new AdminQueueSettingsRequest();
            request.maxConcurrentSelectors = 0;

            assertThrows(IllegalArgumentException.class,
                    () -> adminService.updateQueueSettings(ADMIN_ID, ADMIN_USERNAME, request));
        }

        @Test
        void updateQueueSettings_NegativeMaxConcurrentSelectors_ThrowsIllegalArgumentException() {
            AdminQueueSettingsRequest request = new AdminQueueSettingsRequest();
            request.maxConcurrentSelectors = -5;

            assertThrows(IllegalArgumentException.class,
                    () -> adminService.updateQueueSettings(ADMIN_ID, ADMIN_USERNAME, request));
        }

        @Test
        void updateQueueSettings_ZeroMinutesToStartSelection_ThrowsIllegalArgumentException() {
            AdminQueueSettingsRequest request = new AdminQueueSettingsRequest();
            request.minutesToStartSelection = 0;

            assertThrows(IllegalArgumentException.class,
                    () -> adminService.updateQueueSettings(ADMIN_ID, ADMIN_USERNAME, request));
        }

        @Test
        void updateQueueSettings_OnlyMaxConcurrentSet_CallsOnlyMaxConcurrentSetter() {
            stubAnalyticsDependencies();
            AdminQueueSettingsRequest request = new AdminQueueSettingsRequest();
            request.maxConcurrentSelectors = 20;

            adminService.updateQueueSettings(ADMIN_ID, ADMIN_USERNAME, request);

            verify(queueManager).setMaxConcurrentSelectors(20);
            verify(queueManager, never()).setHowManyMinutesToStartSelection(anyInt());
        }

        @Test
        void updateQueueSettings_OnlyMinutesSet_CallsOnlyMinutesSetter() {
            stubAnalyticsDependencies();
            AdminQueueSettingsRequest request = new AdminQueueSettingsRequest();
            request.minutesToStartSelection = 15;

            adminService.updateQueueSettings(ADMIN_ID, ADMIN_USERNAME, request);

            verify(queueManager).setHowManyMinutesToStartSelection(15);
            verify(queueManager, never()).setMaxConcurrentSelectors(anyInt());
        }

        @Test
        void updateQueueSettings_BothFieldsSet_CallsBothSettersLogsAndReturnsAnalytics() {
            stubAnalyticsDependencies();
            AdminQueueSettingsRequest request = new AdminQueueSettingsRequest();
            request.maxConcurrentSelectors = 20;
            request.minutesToStartSelection = 15;

            AdminAnalyticsDTO result = adminService.updateQueueSettings(ADMIN_ID, ADMIN_USERNAME, request);

            verify(queueManager).setMaxConcurrentSelectors(20);
            verify(queueManager).setHowManyMinutesToStartSelection(15);
            verify(adminRepository).saveActionLog(any(AdminActionLog.class));
            assertNotNull(result);
        }
    }

    // =========================================================================
    // N. getAnalytics Tests
    // =========================================================================

    @Nested
    class GetAnalyticsTests {

        @Test
        void getAnalytics_HappyPath_ReturnsCorrectAggregatedCounts() {
            // Two users: one logged-in, one not logged-in.
            UUID loggedInId  = UUID.randomUUID();
            UUID loggedOutId = UUID.randomUUID();
            User loggedIn  = new User(loggedInId,  "alice", "a@t.com", "h", 25f);
            User loggedOut = new User(loggedOutId, "bob",   "b@t.com", "h", 30f);
            loggedIn.login();

            when(userRepository.getAllUsers())
                    .thenReturn(Map.of(loggedInId, loggedIn, loggedOutId, loggedOut));
            when(companyRepository.getAllActive())
                    .thenReturn(List.of(new Company("c@t.com", "Corp")));
            when(queueManager.getAllQueueSnapshots())
                    .thenReturn(List.of(new QueueManager.QueueSnapshot(UUID.randomUUID(), 3, 1, 10, 10)));
            when(purchaseRepository.findAll()).thenReturn(List.of());
            when(historyRepository.getAll()).thenReturn(List.of());

            AdminAnalyticsDTO result = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

            assertEquals(2, result.registeredUsersCount);
            assertEquals(1, result.loggedInUsersCount);
            assertEquals(1, result.activeCompaniesCount);
            assertEquals(1, result.activeQueuesCount);
            assertEquals(0, result.activePurchasesCount);
            assertEquals(0, result.totalPurchasesCount);
        }

        @Test
        void getAnalytics_LoggedInUsersCount_OnlyCountsUsersWithLoggedInStatus() {
            // Three users: logged-in, not-logged-in, removed — only 1 should be counted.
            UUID u1 = UUID.randomUUID();
            UUID u2 = UUID.randomUUID();
            UUID u3 = UUID.randomUUID();
            User loggedIn    = new User(u1, "a", "a@t.com", "h", 20f);
            User notLoggedIn = new User(u2, "b", "b@t.com", "h", 20f);
            User removed     = new User(u3, "c", "c@t.com", "h", 20f);
            loggedIn.login();
            removed.removeFromPlatformAsAdmin();

            when(userRepository.getAllUsers())
                    .thenReturn(Map.of(u1, loggedIn, u2, notLoggedIn, u3, removed));
            when(companyRepository.getAllActive()).thenReturn(List.of());
            when(queueManager.getAllQueueSnapshots()).thenReturn(List.of());
            when(purchaseRepository.findAll()).thenReturn(List.of());
            when(historyRepository.getAll()).thenReturn(List.of());

            AdminAnalyticsDTO result = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

            assertEquals(1, result.loggedInUsersCount);
        }

        @Test
        void getAnalytics_SnapshotIsPersistedToAdminRepository() {
            when(userRepository.getAllUsers()).thenReturn(Map.of());
            when(companyRepository.getAllActive()).thenReturn(List.of());
            when(queueManager.getAllQueueSnapshots()).thenReturn(List.of());
            when(purchaseRepository.findAll()).thenReturn(List.of());
            when(historyRepository.getAll()).thenReturn(List.of());

            adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

            verify(adminRepository).saveAnalyticsSnapshot(any(SystemAnalyticsSnapshot.class));
        }

        @Test
        void getAnalytics_ReturnedDTO_HasNonNullCreatedAt() {
            when(userRepository.getAllUsers()).thenReturn(Map.of());
            when(companyRepository.getAllActive()).thenReturn(List.of());
            when(queueManager.getAllQueueSnapshots()).thenReturn(List.of());
            when(purchaseRepository.findAll()).thenReturn(List.of());
            when(historyRepository.getAll()).thenReturn(List.of());

            AdminAnalyticsDTO result = adminService.getAnalytics(ADMIN_ID, ADMIN_USERNAME);

            assertNotNull(result.createdAt);
        }
    }
}
