package org.example.ApplicationLayer;

import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.RolesDomainService;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.PolicyManagment.AgeRule;
import org.example.DomainLayer.PolicyManagment.IPurchaseRule;
import org.example.DomainLayer.PolicyManagment.LoneSeatRule;
import org.example.DomainLayer.PolicyManagment.MinTicketRule;
import org.example.DomainLayer.PolicyManagment.OvertDiscount;
import org.example.DomainLayer.PolicyManagment.PurchaseComposite;
import org.example.DomainLayer.UserAggregate.CompanyManager;
import org.example.DomainLayer.UserAggregate.CompanyOwner;
import org.example.DomainLayer.UserAggregate.CompanyFounder;
import org.example.DomainLayer.UserAggregate.User;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CompanyServiceTest {

	@Rule
	public MockitoRule rule = MockitoJUnit.rule().strictness(Strictness.LENIENT);

	@Mock
	private RolesDomainService rolesDomainServiceMock;

	@Mock
	private ICompanyRepository companyRepositoryMock;

	@Mock
	private IUserRepository userRepositoryMock;

	private String adminUsername;
	private String regularUsername;
	private String memberUsername;
	private UUID companyId;
	private CompanyService companyService;
	private RolesDomainService rolesDomainService;
	private PurchaseDomainService purchaseDomainService;

	@Before
	public void setUp() {
		rolesDomainServiceMock = mock(RolesDomainService.class);
		userRepositoryMock = mock(IUserRepository.class);
		// Treat "admin" as a system admin for tests that exercise admin flows

		purchaseDomainService = mock(PurchaseDomainService.class);
		rolesDomainService = new RolesDomainService(companyRepositoryMock, userRepositoryMock);
		companyService = new CompanyService(rolesDomainService, purchaseDomainService);
		adminUsername = "admin";
		regularUsername = "regularUser";
		memberUsername = "member";
		companyId = UUID.randomUUID();

		// Default stub: provide a founder user for tests that reference "founderUser"
		org.example.DomainLayer.UserAggregate.User defaultFounder = new org.example.DomainLayer.UserAggregate.User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 40);
		// by default make him a founder in a dummy company; specific tests override as needed
		defaultFounder.getCompanyRoles().put(companyId, new org.example.DomainLayer.UserAggregate.CompanyFounder("founderUser"));
		org.mockito.Mockito.lenient().when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(defaultFounder));
	}

	/* Test cases for closeCompanyAsAdmin method */
	@Test
	public void testSuccessfulCompanyClosureAsAdmin() {
		String adminUsername = "admin";
		UUID companyId = UUID.randomUUID();

		// Use the mock-wired service
		CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);

		serviceWithMock.closeCompanyAsAdmin(adminUsername, companyId);

		verify(rolesDomainServiceMock, times(1))
				.closeCompanyAsAdmin(adminUsername, companyId);
	}

	@Test
	public void testCompanyNotFound() {
		String adminUsername = "admin";
		UUID companyId = UUID.randomUUID();
		doThrow(new IllegalArgumentException("Company does not exist"))
				.when(rolesDomainServiceMock)
				.closeCompanyAsAdmin(adminUsername, companyId);

		CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);
		assertThrows(IllegalArgumentException.class,
				() -> serviceWithMock.closeCompanyAsAdmin(adminUsername, companyId));
	}

	@Test
	public void testCompanyAlreadyClosed() {
		String adminUsername = "admin";
		UUID companyId = UUID.randomUUID();
		doThrow(new IllegalArgumentException("Company is already closed"))
				.when(rolesDomainServiceMock)
				.closeCompanyAsAdmin(adminUsername, companyId);

		CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);
		assertThrows(IllegalArgumentException.class,
				() -> serviceWithMock.closeCompanyAsAdmin(adminUsername, companyId));
	}

	@Test
	public void testUnauthorizedCompanyClosure() {
		String username = "regularUser";
		UUID companyId = UUID.randomUUID();
		doThrow(new IllegalArgumentException("User is not an admin"))
				.when(rolesDomainServiceMock)
				.closeCompanyAsAdmin(username, companyId);

		CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);
		assertThrows(IllegalArgumentException.class,
				() -> serviceWithMock.closeCompanyAsAdmin(username, companyId));
	}

	@Test
	public void testCloseCompany_AdminUsernameIsNull() {
		UUID companyId = UUID.randomUUID();

		assertThrows(IllegalArgumentException.class, () -> companyService.closeCompanyAsAdmin(null, companyId));

		verifyNoInteractions(rolesDomainServiceMock);
	}

	@Test
	public void testCloseCompany_AdminUsernameIsBlank() {
		UUID companyId = UUID.randomUUID();

		assertThrows(IllegalArgumentException.class, () -> companyService.closeCompanyAsAdmin(" ", companyId));

		verifyNoInteractions(rolesDomainServiceMock);
	}

	/* Test cases for removeCompanyMemberAsAdmin method */

	@Test
	public void testSuccessfulUserRemovalAsAdmin() {
		String adminUsername = "admin";
		String usernameToRemove = "member";

		// Use the mock-wired service
		CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);

		serviceWithMock.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

		verify(rolesDomainServiceMock, times(1))
				.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);
	}

	@Test
	public void testUserNotFound() {
		String adminUsername = "admin";
		String usernameToRemove = "missingUser";
		doThrow(new IllegalArgumentException("User not found"))
				.when(rolesDomainServiceMock)
				.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

		CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);
		assertThrows(IllegalArgumentException.class,
				() -> serviceWithMock.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove));
	}

	@Test
	public void testUserIsNotMember() {
		String adminUsername = "admin";
		String usernameToRemove = "guest";
		doThrow(new IllegalArgumentException("User is not a member"))
				.when(rolesDomainServiceMock)
				.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

		CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);
		assertThrows(IllegalArgumentException.class,
				() -> serviceWithMock.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove));
	}

	@Test
	public void testUnauthorizedUserRemoval() {
		String adminUsername = "regularUser";
		String usernameToRemove = "member";
		doThrow(new IllegalArgumentException("User is not system admin"))
				.when(rolesDomainServiceMock)
				.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

		CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);
		assertThrows(IllegalArgumentException.class,
				() -> serviceWithMock.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove));
	}

	@Test
	public void removeCompanyMemberAsAdmin_whenTwoAdminsRemoveSameUserConcurrently_onlyOneRemovalIsSaved()
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
	public void testRemoveCompanyMember_AdminUsernameIsNull() {
		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsAdmin(null, "member"));

		verifyNoInteractions(rolesDomainServiceMock);
	}

	@Test
	public void testRemoveCompanyMember_AdminUsernameIsBlank() {
		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsAdmin(" ", "member"));

		verifyNoInteractions(rolesDomainServiceMock);
	}

	@Test
	public void testRemoveCompanyMember_UsernameToRemoveIsNull() {
		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsAdmin("admin", null));

		verifyNoInteractions(rolesDomainServiceMock);
	}

	@Test
	public void testRemoveCompanyMember_UsernameToRemoveIsBlank() {
		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsAdmin("admin", " "));

		verifyNoInteractions(rolesDomainServiceMock);
	}

	@Test
	public void testAddPolicyRule_ActuallyPersistsInCompany() {
		// Arrange
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "TestCorp");
		
		User ownerUser = new User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 30);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder("founderUser"));
		when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(ownerUser));
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		Optional<Float> ageLimit = Optional.of(18.0f);

		// Act - true = AND operator (doesn't matter for the first rule)
		companyService.addPolicyRule("founderUser", companyId, ageLimit, Optional.empty(), Optional.empty(),
				Optional.empty(), true);

		// Assert
		IPurchaseRule rootRule = realCompany.getPurchasePolicy().getRulesView();
		assertNotNull("Root rule should not be null", rootRule);
		assertTrue("The root rule should be an instance of AgeRule", rootRule instanceof AgeRule);
	}

	@Test
	public void testAddMultiplePolicyRules_CreatesCompositeTree() {
		// Arrange
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "TestCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		User ownerUser = new User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 30);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder("founderUser"));
		when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(ownerUser));

		// Act: Add an age rule, then a min ticket rule linked with an AND operator (true)
		companyService.addPolicyRule("founderUser", companyId, Optional.of(18.0f), Optional.empty(), Optional.empty(),
				Optional.empty(), true);
		companyService.addPolicyRule("founderUser", companyId, Optional.empty(), Optional.of(2), Optional.empty(),
				Optional.empty(), true);

		// Assert: Check that a tree structure was constructed
		IPurchaseRule rootRule = realCompany.getPurchasePolicy().getRulesView();
		assertTrue("Root rule should now be a PurchaseComposite", rootRule instanceof PurchaseComposite);

		PurchaseComposite composite = (PurchaseComposite) rootRule;
		assertTrue("Left side should contain the original AgeRule", composite.getLeftRule() instanceof AgeRule);
		assertTrue("Right side should contain the new MinTicketRule", composite.getRightRule() instanceof MinTicketRule);
	}

	@Test
	public void testAddOvertDiscount_VerifyStatePersistence() {
		// Arrange
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "DiscountCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		org.example.DomainLayer.UserAggregate.User ownerUser = new org.example.DomainLayer.UserAggregate.User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 40);
		ownerUser.getCompanyRoles().put(companyId, new org.example.DomainLayer.UserAggregate.CompanyFounder("founderUser"));
		when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(ownerUser));

		LocalDate startDate = LocalDate.now();
		LocalDate endDate = LocalDate.now().plusDays(5);
		float discountAmount = 15.5f;

		// Act
		companyService.addOvertDiscount("founderUser", companyId, startDate, endDate, discountAmount);

		// Assert
		var discounts = realCompany.getDiscountPolicy().getDiscountRules();
		assertEquals("Discount rule should be added", 1, discounts.size());
		assertTrue("Rule should be an OvertDiscount", discounts.get(0) instanceof OvertDiscount);
	}

	@Test
	public void testDeleteSpecificPolicyRules() {
		// Arrange
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "DeletionCorp");

		// Manually build an initial tree with 2 rules using true (AND)
		realCompany.addPurchasePolicy(Optional.of(18.0f), Optional.empty(), Optional.empty(), Optional.empty(), true);
		realCompany.addPurchasePolicy(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(true), true);
		
		// Extract the composite root and find the target child ID (AgeRule)
		PurchaseComposite rootComposite = (PurchaseComposite) realCompany.getPurchasePolicy().getRulesView();
		UUID ageRuleId = rootComposite.getLeftRule().getId();

		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		User ownerUser = new User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 40);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder("founderUser"));
		when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(ownerUser));

		// Act: Delete specifically the AgeRule by its ID
		companyService.deletePolicyRule("founderUser", companyId, ageRuleId);

		// Assert: Your PurchaseComposite removes the rule and promotes the remaining child (LoneSeatRule)
		IPurchaseRule remainingRoot = realCompany.getPurchasePolicy().getRulesView();
		assertNotNull("The policy shouldn't be completely empty", remainingRoot);
		assertTrue("The remaining rule promoted to the root should be LoneSeatRule", remainingRoot instanceof LoneSeatRule);
	}

	@Test
	public void testAddPolicyRule_HandlesNullOptionalsGracefully() {
		// Arrange
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "SafetyCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		User ownerUser = new User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 40);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder("founderUser"));
		when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(ownerUser));

		// Act: Pass empty optionals for everything
		companyService.addPolicyRule("founderUser", companyId, Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty(), true);

		// Assert
		IPurchaseRule rootRule = realCompany.getPurchasePolicy().getRulesView();
		assertNull("No rules or composites should be created if all Optionals are empty", rootRule);
	}

	@Test
	public void testDeleteDiscountRule_ActuallyRemovesFromCompanyState() {
		// Arrange
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "DiscountDeleteCorp");

		// We mock the repository to return our real company instance
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		org.example.DomainLayer.UserAggregate.User ownerUser = new org.example.DomainLayer.UserAggregate.User(UUID.randomUUID(), "founderUser", "founderUser", "hash", 40);
		ownerUser.getCompanyRoles().put(companyId, new org.example.DomainLayer.UserAggregate.CompanyFounder("founderUser"));
		when(userRepositoryMock.findByEmail("founderUser")).thenReturn(Optional.of(ownerUser));

		// 1. Add two different discounts
		companyService.addOvertDiscount("founderUser", companyId, LocalDate.now(), LocalDate.now().plusDays(5), 10.0f);
		companyService.addOvertDiscount("founderUser", companyId, LocalDate.now(), LocalDate.now().plusDays(10), 20.0f);

		// 2. Retrieve the rules to get the specific ID of the first one
		var discounts = realCompany.getDiscountPolicy().getDiscountRules();
		assertEquals("Should have 2 discounts initially", 2, discounts.size());
		UUID firstDiscountId = discounts.get(0).getId();

		// Act
		// Assuming your CompanyService has a deleteDiscount method that takes the ID
		// If your service uses a different signature, adjust accordingly:
		rolesDomainService.removeDiscount("founderUser", companyId, firstDiscountId);

		// Assert
		var remainingDiscounts = realCompany.getDiscountPolicy().getDiscountRules();
		assertEquals("Should have only 1 discount left", 1, remainingDiscounts.size());

		// Verify the remaining discount is NOT the one we deleted
		assertNotEquals("The deleted discount ID should no longer be present",
				firstDiscountId,
				remainingDiscounts.get(0).getId());
	}

	@Test
	public void testCreateCompany_ValidInput_CallsDomainService() {
		// Arrange: Create a local version of the service that uses the mock
		// This ignores the 'rolesDomainService' created in @Before
		CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);

		String founder = "moshiko123";
		String companyName = "Workshop Ltd";

		// Act: Use the service instance that is actually linked to the mock
		serviceWithMock.createCompany(founder, companyName);

		// Assert: This will now pass because 'serviceWithMock' actually called the mock
		verify(rolesDomainServiceMock, times(1)).createCompany(founder, companyName);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCreateCompany_NullUsername_ThrowsException() {
		// Arrange: Using the mock-based service for consistency
		CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);

		// Act
		serviceWithMock.createCompany(null, "Some Company");

		// Assert: Handled by expected = IllegalArgumentException.class
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCreateCompany_EmptyUsername_ThrowsException() {
		// Arrange
		CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);

		// Act
		serviceWithMock.createCompany("   ", "Some Company");

		// Assert: Handled by expected = IllegalArgumentException.class
	}

	/* Tests for manager/owner invitations and permissions — verify state changes */

	@Test
	public void testInviteCompanyManager_Valid_AddsManagerAfterAccept() {
		// Arrange: real company wired to repository
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "TestCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));
		String owner = "ownerUser";
		String invitee = "managerUser";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		// create users and wire repository
		User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 40);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder(owner));
		User inviteeUser = new User(UUID.randomUUID(), invitee, invitee, "hash", 30);
		when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
		when(userRepositoryMock.findByEmail(invitee)).thenReturn(Optional.of(inviteeUser));

		// Act: invite and accept via application service
		UUID invitationId = companyService.inviteCompanyManager(owner, companyId, invitee, perms);
		// simulate acceptance as RolesDomainService would
		inviteeUser.acceptCompanyInvitation(invitationId);

		// Assert: invitee became a company member
		assertTrue(inviteeUser.isCompanyMember(companyId));
	}

	@Test
	public void testInviteCompanyManager_NullOwner_ValidationFails() {
		// Arrange
		UUID companyId = UUID.randomUUID();
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		// Act & Assert: validation at application layer
		assertThrows(IllegalArgumentException.class,
			() -> companyService.inviteCompanyManager(null, companyId, "m", perms));

		// Assert: repository not queried
		verifyNoInteractions(companyRepositoryMock);
	}

	@Test
	public void testInviteCompanyOwner_Valid_AddsOwnerAfterAccept() {
		// Arrange: company with founder
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "OwnerCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));
		String owner = "ownerUser";
		String invitee = "newOwner";

		User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 45);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder(owner));
		User inviteeUser = new User(UUID.randomUUID(), invitee, invitee, "hash", 28);
		when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
		when(userRepositoryMock.findByEmail(invitee)).thenReturn(Optional.of(inviteeUser));

		// Act: invite and accept
		UUID invitationId = companyService.inviteCompanyOwner(owner, companyId, invitee);
		inviteeUser.acceptCompanyInvitation(invitationId);

		// Assert: invitee became an owner
		assertTrue(inviteeUser.getCompanyRole(companyId) instanceof CompanyOwner);
	}

	@Test
	public void testInviteCompanyOwner_BlankOwner_ValidationFails() {
		// Arrange
		UUID companyId = UUID.randomUUID();

		// Act & Assert
		assertThrows(IllegalArgumentException.class,
				() -> companyService.inviteCompanyOwner(" ", companyId, "someone"));

		verifyNoInteractions(companyRepositoryMock);
	}

	@Test
	public void testAcceptCompanyInvitation_Valid_AddsMember() {
		// Arrange: create company and invitation via service
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "InviteCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));
		String owner = "ownerUser";
		String invitee = "inviteeUser";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 40);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder(owner));
		User inviteeUser = new User(UUID.randomUUID(), invitee, invitee, "hash", 22);
		when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
		when(userRepositoryMock.findByEmail(invitee)).thenReturn(Optional.of(inviteeUser));

		UUID invitationId = companyService.inviteCompanyManager(owner, companyId, invitee, perms);

		// Act - simulate acceptance
		inviteeUser.acceptCompanyInvitation(invitationId);

		// Assert
		assertTrue(inviteeUser.isCompanyMember(companyId));
	}

	@Test
	public void testAcceptCompanyInvitation_MissingCompany_Throws() {
		// Arrange: repository returns empty
		UUID companyId = UUID.randomUUID();
		UUID invitationId = UUID.randomUUID();
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(Exception.class,
				() -> companyService.acceptCompanyInvitation(invitationId, "inviteeUser", companyId));
	}

	@Test
	public void testSuccessfulManagerAppointment_CreatesInvitationAndPendingUntilAccept() {
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "ManagerCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String owner = "ownerUser";
		String appointee = "newManager";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 38);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder(owner));
		User appointeeUser = new User(UUID.randomUUID(), appointee, appointee, "hash", 26);
		when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
		when(userRepositoryMock.findByEmail(appointee)).thenReturn(Optional.of(appointeeUser));

		UUID invitationId = companyService.inviteCompanyManager(owner, companyId, appointee, perms);

		assertNotNull("Invitation id should be returned", invitationId);
		// Before acceptance the user should NOT be a member
		assertFalse(appointeeUser.isCompanyMember(companyId));

		// simulate acceptance
		appointeeUser.acceptCompanyInvitation(invitationId);

		// After acceptance the appointee becomes a member
		assertTrue(appointeeUser.isCompanyMember(companyId));
	}

	@Test
	public void testAppointeeAlreadyHasRole_Blocked() {
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "DupCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String owner = "ownerUser";
		String appointee = "mgrAlready";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 40);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder(owner));
		User appointeeUser = new User(UUID.randomUUID(), appointee, appointee, "hash", 29);
		// pre-appoint as manager
		appointeeUser.getCompanyRoles().put(companyId, new CompanyManager(appointee, (CompanyOwner) ownerUser.getCompanyRole(companyId), perms));
		when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
		when(userRepositoryMock.findByEmail(appointee)).thenReturn(Optional.of(appointeeUser));

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
			() -> companyService.inviteCompanyManager(owner, companyId, appointee, perms));

		assertEquals("The appointee is already a member of the company and therefore cannot be invited as a manager",
			ex.getMessage());
	}

	@Test
	public void testUnauthorizedManagerAppointment_Rejected() {
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "UnAuthCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String owner = "ownerUser";
		String existingManager = "mgrUser";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.VIEW_HISTORY);

		User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 50);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder(owner));
		User managerUser = new User(UUID.randomUUID(), existingManager, existingManager, "hash", 35);
		managerUser.getCompanyRoles().put(companyId, new CompanyManager(existingManager, (CompanyOwner) ownerUser.getCompanyRole(companyId), perms));
		when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
		when(userRepositoryMock.findByEmail(existingManager)).thenReturn(Optional.of(managerUser));

		// That manager (not an owner) attempts to invite another manager
		String appointee = "attemptedNewMgr";

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
			() -> companyService.inviteCompanyManager(existingManager, companyId, appointee, perms));

		assertEquals("The appointer is not a company owner and therefore cannot invite a new manager", ex.getMessage());
		// Ensure no change in membership
		User appointeeUser = new User(UUID.randomUUID(), appointee, appointee, "hash", 22);
		assertFalse(appointeeUser.isCompanyMember(companyId));
	}

	@Test
	public void testCircularAppointmentPrevention_Blocked() {
		// Use the mocked RolesDomainService to simulate domain-level circularity
		// detection
		CompanyService svcWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);
		UUID companyId = UUID.randomUUID();
		String appointer = "ownerA";
		String appointee = "userB";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		when(rolesDomainServiceMock.inviteCompanyManager(appointer, companyId, appointee, perms))
				.thenThrow(new IllegalArgumentException("Circular appointment detected"));

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> svcWithMock.inviteCompanyManager(appointer, companyId, appointee, perms));

		assertEquals("Circular appointment detected", ex.getMessage());
	}

	@Test
	public void testAppointmentToNonExistentUser_NoInvitationCreated() {
		// Simulate the domain service detecting a non-existent user
		CompanyService svcWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);
		UUID companyId = UUID.randomUUID();
		String appointer = "ownerUser";
		String nonExistent = "ghostUser";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		when(rolesDomainServiceMock.inviteCompanyManager(appointer, companyId, nonExistent, perms))
				.thenThrow(new IllegalArgumentException("User does not exist"));

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> svcWithMock.inviteCompanyManager(appointer, companyId, nonExistent, perms));

		assertEquals("User does not exist", ex.getMessage());
	}

	@Test
	public void testChangeManagerPermissions_Valid_UpdatesPermissions() {
		// Use the mock-based domain service to handle permission change logic
		CompanyService svcWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);
		UUID companyId = UUID.randomUUID();
		String owner = "ownerUser";
		String manager = "mgrUser";

		User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 45);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder(owner));
		Set<CompanyPermission> initial = new HashSet<>();
		initial.add(CompanyPermission.VIEW_HISTORY);
		User managerUser = new User(UUID.randomUUID(), manager, manager, "hash", 33);
		managerUser.getCompanyRoles().put(companyId, new CompanyManager(manager, (CompanyOwner) ownerUser.getCompanyRole(companyId), initial));

		when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
		when(userRepositoryMock.findByEmail(manager)).thenReturn(Optional.of(managerUser));

		Set<CompanyPermission> newPerms = new HashSet<>();
		newPerms.add(CompanyPermission.CONFIGURE_LAYOUT);

		// When domain service is invoked, update the manager's permissions in the test
		doAnswer(invocation -> {
			managerUser.getCompanyRole(companyId).setAppointer((CompanyOwner) ownerUser.getCompanyRole(companyId));
			((CompanyManager) managerUser.getCompanyRole(companyId)).setNewPremissions(newPerms);
			return null;
		}).when(rolesDomainServiceMock).changeManagerPermissions(owner, companyId, manager, newPerms);

		svcWithMock.changeManagerPermissions(owner, companyId, manager, newPerms);

		assertTrue(((CompanyManager) managerUser.getCompanyRole(companyId)).getPremissions().contains(CompanyPermission.CONFIGURE_LAYOUT));
	}

	@Test
	public void testChangeManagerPermissions_OwnerNull_ValidationFails() {
		// Arrange
		UUID companyId = UUID.randomUUID();

		// Act & Assert
		assertThrows(IllegalArgumentException.class,
				() -> companyService.changeManagerPermissions(null, companyId, "m", new HashSet<>()));

		verifyNoInteractions(companyRepositoryMock);
	}

	@Test
	public void testChangeManagerPermissions_ManagerBlank_ValidationFails() {
		// Arrange
		UUID companyId = UUID.randomUUID();

		// Act & Assert
		assertThrows(IllegalArgumentException.class,
				() -> companyService.changeManagerPermissions("owner", companyId, " ",
						new HashSet<>()));

		verifyNoInteractions(companyRepositoryMock);
	}

	@Test
	public void testRemoveCompanyMemberAsOwner_Valid_RemovesMember() {
		// Arrange: real company with a manager under the founder
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "RemovalCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));
		String owner = "ownerUser";
		String removeUser = "memberUser";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 46);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder(owner));
		User removed = new User(UUID.randomUUID(), removeUser, removeUser, "hash", 31);
		removed.getCompanyRoles().put(companyId, new CompanyManager(removeUser, (CompanyOwner) ownerUser.getCompanyRole(companyId), perms));
		when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
		when(userRepositoryMock.findByEmail(removeUser)).thenReturn(Optional.of(removed));

		// Sanity check
		assertTrue(removed.isCompanyMember(companyId));

		// Act
		companyService.removeCompanyMemberAsOwner(owner, companyId, removeUser);

		// Assert
		assertFalse(removed.isCompanyMember(companyId));
	}

	@Test
	public void testRemoveCompanyMemberAsOwner_OwnerBlank_ValidationFails() {
		// Arrange
		UUID companyId = UUID.randomUUID();

		// Act & Assert
		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsOwner(" ", companyId, "u"));

		verifyNoInteractions(companyRepositoryMock);
	}

	@Test
	public void closeCompanyAsAdmin_whenAdminClosesActiveCompany_closesAndSavesCompany() {
		Company company = mock(Company.class);

		when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(company));
		when(company.isActive()).thenReturn(true);
		companyService.closeCompanyAsAdmin(adminUsername, companyId);
		verify(userRepositoryMock).isSystemAdmin(adminUsername);
		verify(companyRepositoryMock).findByID(companyId);
		verify(company).AdminClose();
		verify(companyRepositoryMock).save(company);
	}

	@Test
	public void closeCompanyAsAdmin_whenAdminUsernameIsNull_throwsExceptionAndDoesNotTouchRepositories() {
		assertThrows(IllegalArgumentException.class, () -> companyService.closeCompanyAsAdmin(null, companyId));

		verifyNoInteractions(userRepositoryMock);
		verifyNoInteractions(companyRepositoryMock);
	}

	@Test
	public void closeCompanyAsAdmin_whenAdminUsernameIsBlank_throwsExceptionAndDoesNotTouchRepositories() {
		assertThrows(IllegalArgumentException.class, () -> companyService.closeCompanyAsAdmin(" ", companyId));

		verifyNoInteractions(userRepositoryMock);
		verifyNoInteractions(companyRepositoryMock);
	}

	@Test
	public void closeCompanyAsAdmin_whenUserIsNotSystemAdmin_throwsExceptionAndDoesNotFetchCompany() {
		when(userRepositoryMock.isSystemAdmin(regularUsername)).thenReturn(false);

		assertThrows(IllegalArgumentException.class,
				() -> companyService.closeCompanyAsAdmin(regularUsername, companyId));

		verify(userRepositoryMock).isSystemAdmin(regularUsername);
		verifyNoInteractions(companyRepositoryMock);
	}

	@Test
	public void closeCompanyAsAdmin_whenCompanyDoesNotExist_throwsExceptionAndDoesNotSave() {
		when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.empty());

		assertThrows(IllegalArgumentException.class,
				() -> companyService.closeCompanyAsAdmin(adminUsername, companyId));

		verify(userRepositoryMock).isSystemAdmin(adminUsername);
		verify(companyRepositoryMock).findByID(companyId);
		verify(companyRepositoryMock, never()).save(any());
	}

	@Test
	public void closeCompanyAsAdmin_whenCompanyAlreadyInactive_throwsExceptionAndDoesNotCloseAgain() {
		Company company = mock(Company.class);

		when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(company));
		when(company.isActive()).thenReturn(false);

		assertThrows(IllegalStateException.class, () -> companyService.closeCompanyAsAdmin(adminUsername, companyId));

		verify(userRepositoryMock).isSystemAdmin(adminUsername);
		verify(companyRepositoryMock).findByID(companyId);
		verify(company, never()).AdminClose();
		verify(companyRepositoryMock, never()).save(any());
	}

	@Test
	public void closeCompanyAsAdmin_whenCompanyIdIsNull_throwsExceptionAndDoesNotTouchRepositories() {
		assertThrows(IllegalArgumentException.class,
				() -> companyService.closeCompanyAsAdmin(adminUsername, null));

		verifyNoInteractions(userRepositoryMock);
		verifyNoInteractions(companyRepositoryMock);
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
	}

	/*
	 * removeCompanyMemberAsAdmin tests
	 */

	@Test
	public void removeCompanyMemberAsAdmin_whenAdminRemovesExistingMember_removesFromAllCompaniesAndSaves() {
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
		verify(userRepositoryMock).getCompaniesIdsByMember(memberUsername);
		verify(userRepositoryMock).findByEmail(memberUsername);

		verify(user, times(1)).removeFromCompanyAsAdmin(firstId);
		verify(user, times(1)).removeFromCompanyAsAdmin(secondId);
	}

	@Test
	public void removeCompanyMemberAsAdmin_whenAdminUsernameIsNull_throwsExceptionAndDoesNotTouchRepositories() {
		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsAdmin(null, memberUsername));

		verifyNoInteractions(userRepositoryMock);
		verifyNoInteractions(companyRepositoryMock);
	}

	@Test
	public void removeCompanyMemberAsAdmin_whenAdminUsernameIsBlank_throwsExceptionAndDoesNotTouchRepositories() {
		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsAdmin(" ", memberUsername));

		verifyNoInteractions(userRepositoryMock);
		verifyNoInteractions(companyRepositoryMock);
	}

	@Test
	public void removeCompanyMemberAsAdmin_whenUsernameToRemoveIsNull_throwsExceptionAndDoesNotTouchRepositories() {
		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsAdmin(adminUsername, null));

		verifyNoInteractions(userRepositoryMock);
		verifyNoInteractions(companyRepositoryMock);
	}

	@Test
	public void removeCompanyMemberAsAdmin_whenUsernameToRemoveIsBlank_throwsExceptionAndDoesNotTouchRepositories() {
		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsAdmin(adminUsername, " "));

		verifyNoInteractions(userRepositoryMock);
		verifyNoInteractions(companyRepositoryMock);
	}

	@Test
	public void removeCompanyMemberAsAdmin_whenUserIsNotSystemAdmin_throwsExceptionAndDoesNotFetchCompanies() {
		when(userRepositoryMock.isSystemAdmin(regularUsername)).thenReturn(false);

		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsAdmin(regularUsername, memberUsername));

		verify(userRepositoryMock).isSystemAdmin(regularUsername);
		verify(userRepositoryMock, never()).getCompaniesIdsByMember(anyString());
		verify(companyRepositoryMock, never()).save(any());
	}

	@Test
	public void removeCompanyMemberAsAdmin_whenUserIsNotAssignedToAnyCompany_throwsExceptionAndDoesNotSave() {
		User user = mock(User.class);
		when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
		when(userRepositoryMock.findByEmail(memberUsername)).thenReturn(Optional.of(user));
		when(userRepositoryMock.getCompaniesIdsByMember(memberUsername)).thenReturn(List.of());

		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsAdmin(adminUsername, memberUsername));

		verify(userRepositoryMock).isSystemAdmin(adminUsername);
		verify(userRepositoryMock).getCompaniesIdsByMember(memberUsername);
		verify(companyRepositoryMock, never()).save(any());
	}

	@Test
	public void testSuccessfulManagerRemoval_RemovesManagerAndUpdatesHierarchy() {
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "ManagerRemovalCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String owner = "ownerUser";
		String manager = "managerToRemove";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		// Build users and wire user repository
		User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 41);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder(owner));
		User managerUser = new User(UUID.randomUUID(), manager, manager, "hash", 30);
		managerUser.getCompanyRoles().put(companyId, new CompanyManager(manager, (CompanyOwner) ownerUser.getCompanyRole(companyId), perms));
		when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
		when(userRepositoryMock.findByEmail(manager)).thenReturn(Optional.of(managerUser));

		// Sanity check
		assertTrue(managerUser.isCompanyMember(companyId));

		// Act
		companyService.removeCompanyMemberAsOwner(owner, companyId, manager);

		// Assert: manager removed (treated as subscriber/outside company)
		assertFalse(managerUser.isCompanyMember(companyId));

		// Hierarchy/mermaid should no longer contain the manager
		String mermaid = companyService.getCompanyHierarchyMermaid(companyId, owner);
		assertFalse(mermaid.contains(manager));
	}

	@Test
	public void testUnauthorizedManagerRemoval_BlockedWhenNotDirectAppointer() {
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "AuthRemovalCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String founder = "founderUser";
		String owner1 = "owner1";
		String owner2 = "owner2";

		User founderUser = new User(UUID.randomUUID(), founder, founder, "hash", 55);
		founderUser.getCompanyRoles().put(companyId, new CompanyFounder(founder));
		User owner1User = new User(UUID.randomUUID(), owner1, owner1, "hash", 44);
		owner1User.getCompanyRoles().put(companyId, new CompanyOwner(owner1, (CompanyOwner) founderUser.getCompanyRole(companyId)));
		User owner2User = new User(UUID.randomUUID(), owner2, owner2, "hash", 43);
		owner2User.getCompanyRoles().put(companyId, new CompanyOwner(owner2, (CompanyOwner) founderUser.getCompanyRole(companyId)));

		String manager = "managedByOwner2";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);
		User managerUser = new User(UUID.randomUUID(), manager, manager, "hash", 33);
		managerUser.getCompanyRoles().put(companyId, new CompanyManager(manager, (CompanyOwner) owner2User.getCompanyRole(companyId), perms));

		when(userRepositoryMock.findByEmail(owner1)).thenReturn(Optional.of(owner1User));
		when(userRepositoryMock.findByEmail(owner2)).thenReturn(Optional.of(owner2User));
		when(userRepositoryMock.findByEmail(manager)).thenReturn(Optional.of(managerUser));

		// Attempt removal by owner1 should fail because manager is under owner2
		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsOwner(owner1, companyId, manager));
		// ensure manager still exists
		assertTrue(managerUser.isCompanyMember(companyId));
	}

	@Test
	public void testRolesListUpdateAfterRemoval_ManagerNoLongerListed() {
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "RolesUpdateCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String owner = "ownerUser";
		String manager = "tempManager";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		User ownerUser = new User(UUID.randomUUID(), owner, owner, "hash", 48);
		ownerUser.getCompanyRoles().put(companyId, new CompanyFounder(owner));
		User managerUser = new User(UUID.randomUUID(), manager, manager, "hash", 32);
		managerUser.getCompanyRoles().put(companyId, new CompanyManager(manager, (CompanyOwner) ownerUser.getCompanyRole(companyId), perms));
		when(userRepositoryMock.findByEmail(owner)).thenReturn(Optional.of(ownerUser));
		when(userRepositoryMock.findByEmail(manager)).thenReturn(Optional.of(managerUser));

		assertTrue(managerUser.isCompanyMember(companyId));

		companyService.removeCompanyMemberAsOwner(owner, companyId, manager);

		// The removed user should not appear under "managers" in the hierarchy
		String mermaid = companyService.getCompanyHierarchyMermaid(companyId, owner);
		assertFalse(mermaid.contains(manager));
		assertFalse(managerUser.isCompanyMember(companyId));
	}

	@Test
	public void testSuccessfulRecursiveOwnershipRemoval_RemovesOwnerAndAllSubordinates() {
		// We'll simulate recursive removal at the domain-service level using the mock
		CompanyService svcWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "RecursiveRemovalCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String founder = "founderUser";
		// Create ownerA under founder
		String ownerA = "ownerA";
		String ownerB = "ownerB";
		String managerX = "mgrX";

		User founderUser = new User(UUID.randomUUID(), founder, founder, "hash", 60);
		founderUser.getCompanyRoles().put(companyId, new CompanyFounder(founder));

		User ownerAUser = new User(UUID.randomUUID(), ownerA, ownerA, "hash", 50);
		ownerAUser.getCompanyRoles().put(companyId, new CompanyOwner(ownerA, (CompanyOwner) founderUser.getCompanyRole(companyId)));

		User ownerBUser = new User(UUID.randomUUID(), ownerB, ownerB, "hash", 45);
		ownerBUser.getCompanyRoles().put(companyId, new CompanyOwner(ownerB, (CompanyOwner) ownerAUser.getCompanyRole(companyId)));

		User managerXUser = new User(UUID.randomUUID(), managerX, managerX, "hash", 33);
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);
		managerXUser.getCompanyRoles().put(companyId, new CompanyManager(managerX, (CompanyOwner) ownerBUser.getCompanyRole(companyId), perms));

		when(userRepositoryMock.findByEmail(founder)).thenReturn(Optional.of(founderUser));
		when(userRepositoryMock.findByEmail(ownerA)).thenReturn(Optional.of(ownerAUser));
		when(userRepositoryMock.findByEmail(ownerB)).thenReturn(Optional.of(ownerBUser));
		when(userRepositoryMock.findByEmail(managerX)).thenReturn(Optional.of(managerXUser));

		assertTrue(ownerAUser.isCompanyMember(companyId));
		assertTrue(ownerBUser.isCompanyMember(companyId));
		assertTrue(managerXUser.isCompanyMember(companyId));

		// Mock domain service to perform recursive removal when asked (remove ownerB and managerX)
		doAnswer(invocation -> {
			UUID cid = invocation.getArgument(1);
			String toRemove = invocation.getArgument(2);
			if (toRemove.equals(ownerB)) {
				ownerBUser.getCompanyRoles().remove(cid);
				managerXUser.getCompanyRoles().remove(cid);
			}
			return null;
		}).when(rolesDomainServiceMock).removeCompanyMemberAsOwner(anyString(), any(UUID.class), anyString());

		// Act
		svcWithMock.removeCompanyMemberAsOwner(ownerA, companyId, ownerB);

		// Assert: ownerB and his subordinate managerX are removed
		assertFalse(ownerBUser.isCompanyMember(companyId));
		assertFalse(managerXUser.isCompanyMember(companyId));
	}

	@Test
	public void testUnauthorizedOwnershipRemoval_BlockedWhenNotDirectAppointer() {
		CompanyService svcWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);
		UUID companyId = UUID.randomUUID();

		// Simulate domain service rejecting removal when not direct appointer
		doThrow(new IllegalArgumentException("Only direct appointer may remove"))
				.when(rolesDomainServiceMock).removeCompanyMemberAsOwner("owner1", companyId, "ownerX");

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> svcWithMock.removeCompanyMemberAsOwner("owner1", companyId, "ownerX"));

		assertEquals("Only direct appointer may remove", ex.getMessage());
	}

	@Test
	public void testGenerateSalesReport_ReturnsConsolidatedData() {
		UUID companyId = UUID.randomUUID();
		String owner = "reportOwner";

		UUID event1 = UUID.randomUUID();
		UUID event2 = UUID.randomUUID();
		UUID ticket1 = UUID.randomUUID();
		UUID ticket2 = UUID.randomUUID();

		double revenue = 1234.56;
		SalesReport report = new SalesReport(List.of(event1, event2), List.of(ticket1, ticket2), revenue);

		when(purchaseDomainService.getSalesReportForOwner(owner, companyId)).thenReturn(report);

		String result = companyService.getSalesReportForOwner(owner, companyId);

		assertTrue(result.contains("eventIds="));
		assertTrue(result.contains(event1.toString()));
		assertTrue(result.contains(event2.toString()));
		assertTrue(result.contains("ticketIds="));
		assertTrue(result.contains(ticket1.toString()));
		assertTrue(result.contains(ticket2.toString()));
		assertTrue(result.contains("totalRevenue=" + revenue));
	}

	@Test
	public void testGenerateEmptySalesReport_ProducesZeroTotals_NoCrash() {
		UUID companyId = UUID.randomUUID();
		String owner = "emptyOwner";

		SalesReport empty = new SalesReport(List.of(), List.of(), 0.0);
		when(purchaseDomainService.getSalesReportForOwner(owner, companyId)).thenReturn(empty);

		String result = companyService.getSalesReportForOwner(owner, companyId);

		assertTrue(result.contains("ticketIds=[]") || result.contains("ticketIds="));
		assertTrue(result.contains("totalRevenue=0.0"));
	}

}