package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.CompanyDTOs.HierarchyResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.InvitationResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.SalesReportResponse;
import org.example.ApplicationLayer.dto.SalesReport;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.RolesDomainService;
import org.example.ApplicationLayer.EventService;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.PolicyManagment.AgeRule;
import org.example.DomainLayer.PolicyManagment.IPurchaseRule;
import org.example.DomainLayer.PolicyManagment.LoneSeatRule;
import org.example.DomainLayer.PolicyManagment.MinTicketRule;
import org.example.DomainLayer.PolicyManagment.OvertDiscount;
import org.example.DomainLayer.PolicyManagment.PurchaseComposite;
import org.example.DomainLayer.UserAggregate.CompanyFounder;
import org.example.DomainLayer.UserAggregate.CompanyManager;
import org.example.DomainLayer.UserAggregate.CompanyOwner;
import org.example.DomainLayer.UserAggregate.User;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;


import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
public class CompanyServiceTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    @Mock
    private RolesDomainService rolesDomainServiceMock;

    @Mock
    private ICompanyRepository companyRepositoryMock;

    @Mock
    private IUserRepository userRepositoryMock;

    @Mock
    private PurchaseDomainService purchaseDomainService;

        @Mock
        private EventService eventServiceMock;

    private CompanyService companyService;
    private RolesDomainService rolesDomainService;

    private String adminUsername;
    private String memberUsername;
    private UUID companyId;

    private INotifier mockNotifier;

    @Before
    public void setUp() {
        rolesDomainServiceMock = mock(RolesDomainService.class);
        companyRepositoryMock = mock(ICompanyRepository.class);
        userRepositoryMock = mock(IUserRepository.class);
        purchaseDomainService = mock(PurchaseDomainService.class);
        mockNotifier = mock(INotifier.class);
        eventServiceMock = mock(EventService.class);


        rolesDomainService = new RolesDomainService(companyRepositoryMock, userRepositoryMock);
        companyService = new CompanyService(rolesDomainService, purchaseDomainService, mockNotifier, eventServiceMock);

        adminUsername = "admin";
        memberUsername = "member";
        companyId = UUID.randomUUID();

        User defaultFounder = new User(
                UUID.randomUUID(),
                "founderUser",
                "founderUser",
                "hash",
                40
        );
        defaultFounder.getCompanyRoles().put(companyId, new CompanyFounder("founderUser"));

        lenient().when(userRepositoryMock.findByEmail("founderUser"))
                .thenReturn(Optional.of(defaultFounder));
    }

    // ================================================================
    // createCompany
    // ================================================================


    @Test
    public void testCreateCompany_NullUsername_ThrowsException() {
        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        assertThrows(
                IllegalArgumentException.class,
                () -> serviceWithMock.createCompany(null, "Some Company")
        );
    }

    @Test
    public void testCreateCompany_BlankUsername_ThrowsException() {
        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        assertThrows(
                IllegalArgumentException.class,
                () -> serviceWithMock.createCompany("   ", "Some Company")
        );
    }

    @Test
    public void testCreateCompany_BlankCompanyName_ThrowsException() {
        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        assertThrows(
                IllegalArgumentException.class,
                () -> serviceWithMock.createCompany("founder", " ")
        );
    }

    // ================================================================
    // closeCompanyAsAdmin
    // ================================================================
/*
    @Test
    public void testSuccessfulCompanyClosureAsAdmin_CallsDomainService() {
        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        serviceWithMock.closeCompanyAsAdmin(adminUsername, companyId);

        verify(rolesDomainServiceMock, times(1))
                .closeCompanyAsAdmin(adminUsername, companyId);
    }

    @Test
    public void testCompanyNotFound_WhenClosing_ThrowsException() {
        doThrow(new IllegalArgumentException("Company does not exist"))
                .when(rolesDomainServiceMock)
                .closeCompanyAsAdmin(adminUsername, companyId);

        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        assertThrows(
                IllegalArgumentException.class,
                () -> serviceWithMock.closeCompanyAsAdmin(adminUsername, companyId)
        );
    }

    @Test
    public void testUnauthorizedCompanyClosure_ThrowsException() {
        doThrow(new IllegalArgumentException("User is not an admin"))
                .when(rolesDomainServiceMock)
                .closeCompanyAsAdmin(regularUsername, companyId);

        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        assertThrows(
                IllegalArgumentException.class,
                () -> serviceWithMock.closeCompanyAsAdmin(regularUsername, companyId)
        );
    }

    @Test
    public void closeCompanyAsAdmin_whenAdminClosesActiveCompany_closesAndSavesCompany() {
        Company company = mock(Company.class);

        // Stub system admin check
        when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
        
        // This is called TWICE under the real RolesDomainService implementation workflow: 
        // 1. Inside rolesDomainService.getCompanyOwner(companyId)
        // 2. Inside rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId)
        when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(company));
        
        when(company.isActive()).thenReturn(true);
        
        // FIX: Stub the company name/owner fields or use any() to ensure the internal 
        // rolesDomainService lookup finds a string instead of evaluating to null.
        // Adjust these method names if your real Company domain entity uses getFounder() or getOwner()
        lenient().when(company.getFounderUsername()).thenReturn("founderUser");
        lenient().when(company.getName()).thenReturn("Test Company");

        // Act
        companyService.closeCompanyAsAdmin(adminUsername, companyId);

        // Assert
        verify(userRepositoryMock).isSystemAdmin(adminUsername);

                verify(companyRepositoryMock, times(2)).findByID(companyId);

        
        // Expect findByID to be called exactly 2 times due to the new owner notification lookup
        verify(companyRepositoryMock, times(2)).findByID(companyId);
        
        verify(company).AdminClose();
        verify(companyRepositoryMock).save(company);
        
        // Use any() here instead of anyString() to be entirely safe against null mismatches 
        // during verification across notifications frameworks.
        verify(mockNotifier).notifyUser(any(String.class), contains("has been closed"));
    }

    @Test
    public void closeCompanyAsAdmin_whenAdminUsernameIsNull_throwsExceptionAndDoesNotTouchRepositories() {
        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.closeCompanyAsAdmin(null, companyId)
        );

        verifyNoInteractions(userRepositoryMock);
        verifyNoInteractions(companyRepositoryMock);
    }

    @Test
    public void closeCompanyAsAdmin_whenAdminUsernameIsBlank_throwsExceptionAndDoesNotTouchRepositories() {
        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.closeCompanyAsAdmin(" ", companyId)
        );

        verifyNoInteractions(userRepositoryMock);
        verifyNoInteractions(companyRepositoryMock);
    }

    @Test
    public void closeCompanyAsAdmin_whenCompanyIdIsNull_throwsExceptionAndDoesNotTouchRepositories() {
        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.closeCompanyAsAdmin(adminUsername, null)
        );

        verifyNoInteractions(userRepositoryMock);
        verifyNoInteractions(companyRepositoryMock);
    }

@Test
    public void closeCompanyAsAdmin_whenUserIsNotSystemAdmin_throwsExceptionAndDoesNotFetchCompany() {
        // Arrange
        Company company = mock(Company.class);
        
        // Explicitly stub the non-admin flag condition
        when(userRepositoryMock.isSystemAdmin(regularUsername)).thenReturn(false);
        
        // We must stub this lookup because CompanyService now queries for the owner 
        // before invoking the permission check block inside closeCompanyAsAdmin.
        when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(company));

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.closeCompanyAsAdmin(regularUsername, companyId)
        );

        // Verifications
        verify(userRepositoryMock).isSystemAdmin(regularUsername);
        
        // Change: Change verification from 'verifyNoInteractions' to expect 
        // the single lookup query triggered by the newly added notification workflow.
        verify(companyRepositoryMock, times(1)).findByID(companyId);
        
        // Verify that it short-circuited and never proceeded to execute the actual close save logic
        verify(company, never()).AdminClose();
        verify(companyRepositoryMock, never()).save(any());
        verifyNoInteractions(mockNotifier);
    }

    @Test
    public void closeCompanyAsAdmin_whenCompanyAlreadyInactive_throwsExceptionAndDoesNotCloseAgain() {
        Company company = mock(Company.class);

        when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
        when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(company));
        when(company.isActive()).thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> companyService.closeCompanyAsAdmin(adminUsername, companyId)
        );

        verify(company, never()).AdminClose();
        verify(companyRepositoryMock, never()).save(any());
    }

    @Test
    public void closeCompanyAsAdmin_whenTwoAdminsCloseSameCompanyConcurrently_onlyOneCloseIsSaved()
            throws InterruptedException {
        Company company = mock(Company.class);
        AtomicBoolean active = new AtomicBoolean(true);
        CountDownLatch startTogether = new CountDownLatch(1);

        when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
        when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(company));
        when(company.isActive()).thenAnswer(invocation -> active.get());

        doAnswer(invocation -> {
            Thread.sleep(50);
            active.set(false);
            return null;
        }).when(company).AdminClose();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable closeTask = () -> {
            try {
                startTogether.await();
                companyService.closeCompanyAsAdmin(adminUsername, companyId);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        };

        Thread firstThread = new Thread(closeTask);
        Thread secondThread = new Thread(closeTask);

        firstThread.start();
        secondThread.start();

        startTogether.countDown();

        firstThread.join();
        secondThread.join();

        assertEquals(1, successCount.get());
        assertEquals(1, failureCount.get());

        verify(company, times(1)).AdminClose();
        verify(companyRepositoryMock, times(1)).save(company);
    }*/

    // ================================================================
    // removeCompanyMemberAsAdmin
    // ================================================================

    @Test
    public void testSuccessfulUserRemovalAsAdmin_CallsDomainService() {
        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        serviceWithMock.removeCompanyMemberAsAdmin(adminUsername, memberUsername);

        verify(rolesDomainServiceMock, times(1))
                .removeCompanyMemberAsAdmin(adminUsername, memberUsername);
    }

    @Test
    public void testUserNotFound_WhenRemovingAsAdmin_ThrowsException() {
        doThrow(new IllegalArgumentException("User not found"))
                .when(rolesDomainServiceMock)
                .removeCompanyMemberAsAdmin(adminUsername, "missingUser");

        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        assertThrows(
                IllegalArgumentException.class,
                () -> serviceWithMock.removeCompanyMemberAsAdmin(adminUsername, "missingUser")
        );
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenAdminRemovesExistingMember_removesFromAllCompanies() {
        Company firstCompany = mock(Company.class);
        Company secondCompany = mock(Company.class);

        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        User user = mock(User.class);

        when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
        when(userRepositoryMock.findByEmail(memberUsername)).thenReturn(Optional.of(user));
        when(userRepositoryMock.getCompaniesIdsByMember(memberUsername))
                .thenReturn(List.of(firstId, secondId));

        when(companyRepositoryMock.findByID(firstId)).thenReturn(Optional.of(firstCompany));
        when(companyRepositoryMock.findByID(secondId)).thenReturn(Optional.of(secondCompany));

        companyService.removeCompanyMemberAsAdmin(adminUsername, memberUsername);

        verify(userRepositoryMock).isSystemAdmin(adminUsername);
        verify(userRepositoryMock).findByEmail(memberUsername);
        verify(userRepositoryMock).getCompaniesIdsByMember(memberUsername);

        verify(user, times(1)).removeFromCompanyAsAdmin(firstId);
        verify(user, times(1)).removeFromCompanyAsAdmin(secondId);
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserIsNotAssignedToAnyCompany_throwsException() {
        User user = mock(User.class);

        when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
        when(userRepositoryMock.findByEmail(memberUsername)).thenReturn(Optional.of(user));
        when(userRepositoryMock.getCompaniesIdsByMember(memberUsername)).thenReturn(List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.removeCompanyMemberAsAdmin(adminUsername, memberUsername)
        );

        verify(companyRepositoryMock, never()).save(any());
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenTwoAdminsRemoveSameUserConcurrently_onlyOneRemovalHappens()
            throws InterruptedException {
        Company company = mock(Company.class);
        AtomicBoolean memberAssigned = new AtomicBoolean(true);
        CountDownLatch startTogether = new CountDownLatch(1);

        UUID cid = UUID.randomUUID();
        User user = mock(User.class);

        when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
        when(userRepositoryMock.findByEmail(memberUsername)).thenReturn(Optional.of(user));

        when(userRepositoryMock.getCompaniesIdsByMember(memberUsername)).thenAnswer(invocation -> {
            if (memberAssigned.get()) {
                return List.of(cid);
            }
            return List.of();
        });

        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(company));

        doAnswer(invocation -> {
            Thread.sleep(50);
            memberAssigned.set(false);
            return null;
        }).when(user).removeFromCompanyAsAdmin(cid);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Runnable removeTask = () -> {
            try {
                startTogether.await();
                companyService.removeCompanyMemberAsAdmin(adminUsername, memberUsername);
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        };

        Thread firstThread = new Thread(removeTask);
        Thread secondThread = new Thread(removeTask);

        firstThread.start();
        secondThread.start();

        startTogether.countDown();

        firstThread.join();
        secondThread.join();

        assertEquals(1, successCount.get());
        assertEquals(1, failureCount.get());

        verify(user, times(1)).removeFromCompanyAsAdmin(cid);
    }

    @Test
    public void removeCompanyMemberAsAdmin_validationFailures() {
        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.removeCompanyMemberAsAdmin(null, memberUsername)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.removeCompanyMemberAsAdmin(" ", memberUsername)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.removeCompanyMemberAsAdmin(adminUsername, null)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.removeCompanyMemberAsAdmin(adminUsername, " ")
        );
    }

    // ================================================================
    // policy rules
    // ================================================================

    @Test
    public void testAddPolicyRule_ActuallyPersistsInCompany() {
        UUID cid = UUID.randomUUID();
        Company realCompany = new Company("founderUser", "TestCorp");

        User ownerUser = new User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 30);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder("founderUser"));

        when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(ownerUser));
        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(realCompany));

        companyService.addPolicyRule(
                "founderUser",
                cid,
                Optional.of(18.0f),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true
        );

        IPurchaseRule rootRule = realCompany.getPurchasePolicy().getRulesView();

        assertNotNull(rootRule);
        assertTrue(rootRule instanceof AgeRule);
    }

    @Test
    public void testAddMultiplePolicyRules_CreatesCompositeTree() {
        UUID cid = UUID.randomUUID();
        Company realCompany = new Company("founderUser", "TestCorp");

        User ownerUser = new User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 30);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder("founderUser"));

        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(realCompany));
        when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(ownerUser));

        companyService.addPolicyRule(
                "founderUser",
                cid,
                Optional.of(18.0f),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true
        );

        companyService.addPolicyRule(
                "founderUser",
                cid,
                Optional.empty(),
                Optional.of(2),
                Optional.empty(),
                Optional.empty(),
                true
        );

        IPurchaseRule rootRule = realCompany.getPurchasePolicy().getRulesView();

        assertTrue(rootRule instanceof PurchaseComposite);

        PurchaseComposite composite = (PurchaseComposite) rootRule;

        assertTrue(composite.getLeftRule() instanceof AgeRule);
        assertTrue(composite.getRightRule() instanceof MinTicketRule);
    }

    @Test
    public void testAddPolicyRule_HandlesEmptyOptionalsGracefully() {
        UUID cid = UUID.randomUUID();
        Company realCompany = new Company("founderUser", "SafetyCorp");

        User ownerUser = new User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 40);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder("founderUser"));

        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(realCompany));
        when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(ownerUser));

        companyService.addPolicyRule(
                "founderUser",
                cid,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true
        );

        IPurchaseRule rootRule = realCompany.getPurchasePolicy().getRulesView();

        assertNull(rootRule);
    }

    @Test
    public void testDeleteSpecificPolicyRule_RemovesRuleFromCompanyState() {
        UUID cid = UUID.randomUUID();
        Company realCompany = new Company("founderUser", "DeletionCorp");

        realCompany.addPurchasePolicy(
                Optional.of(18.0f),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true
        );

        realCompany.addPurchasePolicy(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(true),
                true
        );

        PurchaseComposite rootComposite =
                (PurchaseComposite) realCompany.getPurchasePolicy().getRulesView();

        UUID ageRuleId = rootComposite.getLeftRule().getId();

        User ownerUser = new User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 40);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder("founderUser"));

        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(realCompany));
        when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(ownerUser));

        companyService.deletePolicyRule("founderUser", cid, ageRuleId);

        IPurchaseRule remainingRoot = realCompany.getPurchasePolicy().getRulesView();

        assertNotNull(remainingRoot);
        assertTrue(remainingRoot instanceof LoneSeatRule);
    }

    // ================================================================
    // discounts
    // ================================================================

    @Test
    public void testAddOvertDiscount_VerifyStatePersistence() {
        UUID cid = UUID.randomUUID();
        Company realCompany = new Company("founderUser", "DiscountCorp");

        User ownerUser = new User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 40);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder("founderUser"));

        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(realCompany));
        when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(ownerUser));

        companyService.addOvertDiscount(
                "founderUser",
                cid,
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                15.5f
        );

        var discounts = realCompany.getDiscountPolicy().getDiscountRules();

        assertEquals(1, discounts.size());
        assertTrue(discounts.get(0) instanceof OvertDiscount);
    }

    @Test
    public void testDeleteDiscountRule_ActuallyRemovesFromCompanyState() {
        UUID cid = UUID.randomUUID();
        Company realCompany = new Company("founderUser", "DiscountDeleteCorp");

        User ownerUser = new User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 40);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder("founderUser"));

        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(realCompany));
        when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(ownerUser));

        companyService.addOvertDiscount(
                "founderUser",
                cid,
                LocalDate.now(),
                LocalDate.now().plusDays(5),
                10.0f
        );

        companyService.addOvertDiscount(
                "founderUser",
                cid,
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                20.0f
        );

        var discounts = realCompany.getDiscountPolicy().getDiscountRules();

        assertEquals(2, discounts.size());

        UUID firstDiscountId = discounts.get(0).getId();

        companyService.removeDiscount("founderUser", cid, firstDiscountId);

        var remainingDiscounts = realCompany.getDiscountPolicy().getDiscountRules();

        assertEquals(1, remainingDiscounts.size());
        assertNotEquals(firstDiscountId, remainingDiscounts.get(0).getId());
    }

    // ================================================================
    // invitations
    // ================================================================

    @Test
    public void testInviteCompanyManager_Valid_ReturnsInvitationResponseAndAddsManagerAfterAccept() {
        UUID cid = UUID.randomUUID();
        Company realCompany = new Company("ownerUser", "TestCorp");

        String owner = "ownerUser";
        String invitee = "managerUser";

        Set<CompanyPermission> perms = new HashSet<>();
        perms.add(CompanyPermission.MANAGE_POLICIES);

        User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 40);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder(owner));

        User inviteeUser = new User(UUID.randomUUID(), invitee, invitee, "hash", 30);

        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(realCompany));
        when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
        when(userRepositoryMock.findByEmail(invitee)).thenReturn(Optional.of(inviteeUser));

        InvitationResponse invitation =
                companyService.inviteCompanyManager(owner, cid, invitee, perms);

        assertNotNull(invitation);
        assertNotNull(invitation.invitationId);
        assertEquals(cid, invitation.companyId);
        assertEquals(owner, invitation.appointerUsername);
        assertEquals(invitee, invitation.appointeeUsername);
        assertEquals("MANAGER", invitation.invitationType);
        assertEquals(perms, invitation.permissions);

        assertFalse(inviteeUser.isCompanyMember(cid));

        inviteeUser.acceptCompanyInvitation(invitation.invitationId);

        assertTrue(inviteeUser.isCompanyMember(cid));
        assertTrue(inviteeUser.getCompanyRole(cid) instanceof CompanyManager);
    }

    @Test
    public void testInviteCompanyOwner_Valid_ReturnsInvitationResponseAndAddsOwnerAfterAccept() {
        UUID cid = UUID.randomUUID();
        Company realCompany = new Company("ownerUser", "OwnerCorp");

        String owner = "ownerUser";
        String invitee = "newOwner";

        User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 45);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder(owner));

        User inviteeUser = new User(UUID.randomUUID(), invitee, invitee, "hash", 28);

        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(realCompany));
        when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
        when(userRepositoryMock.findByEmail(invitee)).thenReturn(Optional.of(inviteeUser));

        InvitationResponse invitation =
                companyService.inviteCompanyOwner(owner, cid, invitee);

        assertNotNull(invitation);
        assertNotNull(invitation.invitationId);
        assertEquals(cid, invitation.companyId);
        assertEquals(owner, invitation.appointerUsername);
        assertEquals(invitee, invitation.appointeeUsername);
        assertEquals("OWNER", invitation.invitationType);
        assertNull(invitation.permissions);

        inviteeUser.acceptCompanyInvitation(invitation.invitationId);

        assertTrue(inviteeUser.getCompanyRole(cid) instanceof CompanyOwner);
    }

    @Test
    public void testAcceptCompanyInvitation_Valid_AddsMemberThroughService() {
        UUID cid = UUID.randomUUID();
        Company realCompany = new Company("ownerUser", "InviteCorp");

        String owner = "ownerUser";
        String invitee = "inviteeUser";

        Set<CompanyPermission> perms = new HashSet<>();
        perms.add(CompanyPermission.MANAGE_POLICIES);

        User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 40);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder(owner));

        User inviteeUser = new User(UUID.randomUUID(), invitee, invitee, "hash", 22);

        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(realCompany));
        when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
        when(userRepositoryMock.findByEmail(invitee)).thenReturn(Optional.of(inviteeUser));

        InvitationResponse invitation =
                companyService.inviteCompanyManager(owner, cid, invitee, perms);

        companyService.acceptCompanyInvitation(invitation.invitationId, invitee, cid);

        assertTrue(inviteeUser.isCompanyMember(cid));
        assertTrue(inviteeUser.getCompanyRole(cid) instanceof CompanyManager);
    }

    @Test
    public void testInviteCompanyManager_NullOwner_ValidationFails() {
        UUID cid = UUID.randomUUID();

        Set<CompanyPermission> perms = new HashSet<>();
        perms.add(CompanyPermission.MANAGE_POLICIES);

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.inviteCompanyManager(null, cid, "manager", perms)
        );
    }

    @Test
    public void testInviteCompanyOwner_BlankOwner_ValidationFails() {
        UUID cid = UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.inviteCompanyOwner(" ", cid, "someone")
        );
    }

    @Test
    public void testAppointeeAlreadyHasRole_Blocked() {
        UUID cid = UUID.randomUUID();
        Company realCompany = new Company("ownerUser", "DupCorp");

        String owner = "ownerUser";
        String appointee = "mgrAlready";

        Set<CompanyPermission> perms = new HashSet<>();
        perms.add(CompanyPermission.MANAGE_POLICIES);

        User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 40);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder(owner));

        User appointeeUser = new User(UUID.randomUUID(), appointee, appointee, "hash", 29);
        appointeeUser.getCompanyRoles().put(
                cid,
                new CompanyManager(appointee, (CompanyOwner) ownerUser.getCompanyRole(cid), perms)
        );

        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(realCompany));
        when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
        when(userRepositoryMock.findByEmail(appointee)).thenReturn(Optional.of(appointeeUser));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> companyService.inviteCompanyManager(owner, cid, appointee, perms)
        );

        assertEquals(
                "The appointee is already a member of the company and therefore cannot be invited as a manager",
                ex.getMessage()
        );
    }

    @Test
    public void testUnauthorizedManagerAppointment_Rejected() {
        UUID cid = UUID.randomUUID();
        Company realCompany = new Company("ownerUser", "UnAuthCorp");

        String owner = "ownerUser";
        String existingManager = "mgrUser";
        String appointee = "attemptedNewMgr";

        Set<CompanyPermission> perms = new HashSet<>();
        perms.add(CompanyPermission.VIEW_HISTORY);

        User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 50);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder(owner));

        User managerUser = new User(UUID.randomUUID(), existingManager, existingManager, "hash", 35);
        managerUser.getCompanyRoles().put(
                cid,
                new CompanyManager(existingManager, (CompanyOwner) ownerUser.getCompanyRole(cid), perms)
        );

        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(realCompany));
        when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
        when(userRepositoryMock.findByEmail(existingManager)).thenReturn(Optional.of(managerUser));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> companyService.inviteCompanyManager(existingManager, cid, appointee, perms)
        );

        assertEquals(
                "The appointer is not a company owner and therefore cannot invite a new manager",
                ex.getMessage()
        );
    }

    @Test
    public void testCircularAppointmentPrevention_Blocked() {
        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        UUID cid = UUID.randomUUID();
        String appointer = "ownerA";
        String appointee = "userB";

        Set<CompanyPermission> perms = new HashSet<>();
        perms.add(CompanyPermission.MANAGE_POLICIES);

        when(rolesDomainServiceMock.inviteCompanyManager(appointer, cid, appointee, perms))
                .thenThrow(new IllegalArgumentException("Circular appointment detected"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> serviceWithMock.inviteCompanyManager(appointer, cid, appointee, perms)
        );

        assertEquals("Circular appointment detected", ex.getMessage());
    }

    @Test
    public void testAppointmentToNonExistentUser_NoInvitationCreated() {
        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        UUID cid = UUID.randomUUID();
        String appointer = "ownerUser";
        String nonExistent = "ghostUser";

        Set<CompanyPermission> perms = new HashSet<>();
        perms.add(CompanyPermission.MANAGE_POLICIES);

        when(rolesDomainServiceMock.inviteCompanyManager(appointer, cid, nonExistent, perms))
                .thenThrow(new IllegalArgumentException("User does not exist"));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> serviceWithMock.inviteCompanyManager(appointer, cid, nonExistent, perms)
        );

        assertEquals("User does not exist", ex.getMessage());
    }

    // ================================================================
    // permissions
    // ================================================================

    @Test
    public void testChangeManagerPermissions_Valid_UpdatesPermissions() {
        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        UUID cid = UUID.randomUUID();

        String owner = "ownerUser";
        String manager = "mgrUser";

        User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 45);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder(owner));

        Set<CompanyPermission> initial = new HashSet<>();
        initial.add(CompanyPermission.VIEW_HISTORY);

        User managerUser = new User(UUID.randomUUID(), manager, manager, "hash", 33);
        managerUser.getCompanyRoles().put(
                cid,
                new CompanyManager(manager, (CompanyOwner) ownerUser.getCompanyRole(cid), initial)
        );

        Set<CompanyPermission> newPerms = new HashSet<>();
        newPerms.add(CompanyPermission.CONFIGURE_LAYOUT);

        doAnswer(invocation -> {
            ((CompanyManager) managerUser.getCompanyRole(cid)).setNewPremissions(newPerms);
            return null;
        }).when(rolesDomainServiceMock)
                .changeManagerPermissions(owner, cid, manager, newPerms);

        serviceWithMock.changeManagerPermissions(owner, cid, manager, newPerms);

        assertTrue(
                ((CompanyManager) managerUser.getCompanyRole(cid))
                        .getPremissions()
                        .contains(CompanyPermission.CONFIGURE_LAYOUT)
        );
    }

    @Test
    public void testChangeManagerPermissions_ValidationFailures() {
        UUID cid = UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.changeManagerPermissions(null, cid, "manager", new HashSet<>())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.changeManagerPermissions("owner", cid, " ", new HashSet<>())
        );
    }

    // ================================================================
    // remove as owner
    // ================================================================

    @Test
    public void testRemoveCompanyMemberAsOwner_Valid_RemovesMember() {
        UUID cid = UUID.randomUUID();
        Company realCompany = new Company("ownerUser", "RemovalCorp");

        String owner = "ownerUser";
        String removeUser = "memberUser";

        Set<CompanyPermission> perms = new HashSet<>();
        perms.add(CompanyPermission.MANAGE_POLICIES);

        User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 46);
        ownerUser.getCompanyRoles().put(cid, new CompanyFounder(owner));

        User removed = new User(UUID.randomUUID(), removeUser, removeUser, "hash", 31);
        removed.getCompanyRoles().put(
                cid,
                new CompanyManager(removeUser, (CompanyOwner) ownerUser.getCompanyRole(cid), perms)
        );

        when(companyRepositoryMock.findByID(cid)).thenReturn(Optional.of(realCompany));
        when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
        when(userRepositoryMock.findByEmail(removeUser)).thenReturn(Optional.of(removed));

        assertTrue(removed.isCompanyMember(cid));

        companyService.removeCompanyMemberAsOwner(owner, cid, removeUser);

        assertFalse(removed.isCompanyMember(cid));
    }

    @Test
    public void testRemoveCompanyMemberAsOwner_OwnerBlank_ValidationFails() {
        UUID cid = UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.removeCompanyMemberAsOwner(" ", cid, "user")
        );
    }

    @Test
    public void testUnauthorizedOwnershipRemoval_BlockedWhenNotDirectAppointer() {
        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        UUID cid = UUID.randomUUID();

        doThrow(new IllegalArgumentException("Only direct appointer may remove"))
                .when(rolesDomainServiceMock)
                .removeCompanyMemberAsOwner("owner1", cid, "ownerX");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> serviceWithMock.removeCompanyMemberAsOwner("owner1", cid, "ownerX")
        );

        assertEquals("Only direct appointer may remove", ex.getMessage());
    }

    // ================================================================
    // hierarchy
    // ================================================================

    @Test
    public void testGetCompanyHierarchyMermaid_ReturnsHierarchyResponse() {
        CompanyService serviceWithMock =
                new CompanyService(rolesDomainServiceMock, purchaseDomainService, mockNotifier, eventServiceMock);

        UUID cid = UUID.randomUUID();
        String requester = "ownerUser";
        String mermaid = "graph TD\nFounder-->Owner";

        when(rolesDomainServiceMock.getCompanyHierarchyMermaid(cid, requester))
                .thenReturn(mermaid);

        HierarchyResponse response =
                serviceWithMock.getCompanyHierarchyMermaid(cid, requester);

        assertNotNull(response);
        assertEquals(cid, response.companyId);
        assertEquals(mermaid, response.mermaidChart);
    }

    @Test
    public void testGetCompanyHierarchyMermaid_RequesterBlank_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.getCompanyHierarchyMermaid(companyId, " ")
        );
    }

    // ================================================================
    // sales report
    // ================================================================

    @Test
    public void testGenerateSalesReport_ReturnsConsolidatedData() {
        UUID cid = UUID.randomUUID();
        String owner = "reportOwner";

        UUID event1 = UUID.randomUUID();
        UUID event2 = UUID.randomUUID();
        UUID ticket1 = UUID.randomUUID();
        UUID ticket2 = UUID.randomUUID();

        double revenue = 1234.56;

        SalesReport report =
                new SalesReport(List.of(event1, event2), List.of(ticket1, ticket2), revenue);

        when(purchaseDomainService.getSalesReportForOwner(owner, cid))
                .thenReturn(report);

        SalesReportResponse response =
                companyService.getSalesReportForOwner(owner, cid);

        assertNotNull(response);
        assertEquals(cid, response.companyId);
        assertEquals(owner, response.ownerEmail);

        assertEquals(List.of(event1, event2), response.eventIds);
        assertEquals(List.of(ticket1, ticket2), response.ticketIds);
        assertEquals(revenue, response.totalRevenue, 0.0001);
    }

    @Test
    public void testGenerateEmptySalesReport_ProducesZeroTotals_NoCrash() {
        UUID cid = UUID.randomUUID();
        String owner = "emptyOwner";

        SalesReport empty =
                new SalesReport(List.of(), List.of(), 0.0);

        when(purchaseDomainService.getSalesReportForOwner(owner, cid))
                .thenReturn(empty);

        SalesReportResponse response =
                companyService.getSalesReportForOwner(owner, cid);

        assertNotNull(response);
        assertEquals(cid, response.companyId);
        assertEquals(owner, response.ownerEmail);

        assertTrue(response.eventIds.isEmpty());
        assertTrue(response.ticketIds.isEmpty());
        assertEquals(0.0, response.totalRevenue, 0.0001);
    }

    @Test
    public void testGenerateSalesReport_BlankOwner_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> companyService.getSalesReportForOwner(" ", companyId)
        );
    }
}