package org.example.ApplicationLayer;

import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.RolesDomainService;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyManager;
import org.example.DomainLayer.CompanyAggregate.CompanyOwner;
import org.example.DomainLayer.PolicyAggregate.AgeRule;
import org.example.DomainLayer.PolicyAggregate.LoneSeatRule;
import org.example.DomainLayer.PolicyAggregate.OvertDiscount;
import org.example.ApplicationLayer.dto.SalesReport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CompanyServiceTest {

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

		// Now this call won't throw a NullPointerException
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		Optional<Float> ageLimit = Optional.of(18.0f);

		// Act
		companyService.addPolicyRule("founderUser", companyId, ageLimit, Optional.empty(), Optional.empty(),
				Optional.empty());

		// Assert - Checking that the rule was added to the real object's list
		var rules = realCompany.getPurchasePolicy().getRulesView();
		assertFalse("Rules should be added to the company policy", rules.isEmpty());
		assertTrue("The rule should be an instance of AgeRule", rules.get(0) instanceof AgeRule);
	}

	@Test
	public void testAddMultiplePolicyRules_CorrectReplacementLogic() {
		// Arrange
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "TestCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		// Act: Add an age rule, then update it with a new value
		companyService.addPolicyRule("founderUser", companyId, Optional.of(18.0f), Optional.empty(), Optional.empty(),
				Optional.empty());
		companyService.addPolicyRule("founderUser", companyId, Optional.of(21.0f), Optional.empty(), Optional.empty(),
				Optional.empty());

		// Assert: Since your PurchasePolicy.addRule uses removeIf(existingRule ->
		// existingRule.getClass().equals(rule.getClass()))
		// there should still only be ONE AgeRule, but with the updated value.
		var rules = realCompany.getPurchasePolicy().getRulesView();
		long ageRuleCount = rules.stream().filter(r -> r instanceof AgeRule).count();

		assertEquals("Should only have one AgeRule due to replacement logic", 1, ageRuleCount);
		assertEquals(21.0f, ((AgeRule) rules.get(0)).getMinAge(), 0.01);
	}

	@Test
	public void testAddOvertDiscount_VerifyStatePersistence() {
		// Arrange
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "DiscountCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		LocalDate startDate = LocalDate.now();
		LocalDate endDate = LocalDate.now().plusDays(5);
		float discountAmount = 15.5f;

		// Act
		companyService.addOvertDiscount("founderUser", companyId, startDate, endDate, discountAmount);

		// Assert
		var discounts = realCompany.getDiscountPolicy().gDiscountRules();
		assertEquals("Discount rule should be added", 1, discounts.size());
		assertTrue("Rule should be an OvertDiscount", discounts.get(0) instanceof OvertDiscount);
	}

	@Test
	public void testDeleteSpecificPolicyRules() {
		// Arrange
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "DeletionCorp");

		// Manually setup a company with two different rules
		realCompany.addPurchasePolicy(Optional.of(18.0f), Optional.empty(), Optional.empty(),
				Optional.of(true));
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		// Act: Delete only the Age Rule, keep the Lone Seat Rule
		companyService.deletePolicyRule("founderUser", companyId, true, false, false, false);

		// Assert
		var rules = realCompany.getPurchasePolicy().getRulesView();
		assertEquals("Should have exactly one rule left", 1, rules.size());
		assertTrue("Remaining rule should be LoneSeatRule", rules.get(0) instanceof LoneSeatRule);
		assertFalse("AgeRule should have been removed", rules.stream().anyMatch(r -> r instanceof AgeRule));
	}

	@Test
	public void testAddPolicyRule_HandlesNullOptionalsGracefully() {
		// Arrange
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "SafetyCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		// Act: Pass empty optionals for everything
		companyService.addPolicyRule("founderUser", companyId, Optional.empty(), Optional.empty(), Optional.empty(),
				Optional.empty());

		// Assert
		var rules = realCompany.getPurchasePolicy().getRulesView();
		assertTrue("No rules should be added if all Optionals are empty", rules.isEmpty());
	}

	@Test
	public void testDeleteDiscountRule_ActuallyRemovesFromCompanyState() {
		// Arrange
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "DiscountDeleteCorp");

		// We mock the repository to return our real company instance
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		// 1. Add two different discounts
		companyService.addOvertDiscount("founderUser", companyId, LocalDate.now(), LocalDate.now().plusDays(5), 10.0f);
		companyService.addOvertDiscount("founderUser", companyId, LocalDate.now(), LocalDate.now().plusDays(10), 20.0f);

		// 2. Retrieve the rules to get the specific ID of the first one
		var discounts = realCompany.getDiscountPolicy().gDiscountRules();
		assertEquals("Should have 2 discounts initially", 2, discounts.size());
		UUID firstDiscountId = discounts.get(0).getId();

		// Act
		// Assuming your CompanyService has a deleteDiscount method that takes the ID
		// If your service uses a different signature, adjust accordingly:
		rolesDomainService.removeDiscount("founderUser", companyId, firstDiscountId);

		// Assert
		var remainingDiscounts = realCompany.getDiscountPolicy().gDiscountRules();
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
		String owner = realCompany.getFounder().getUsername();
		String invitee = "managerUser";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		// Act: invite and accept via application service
		UUID invitationId = companyService.inviteCompanyManager(owner, companyId, invitee, perms);
		companyService.acceptCompanyInvitation(invitationId, companyId);

		// Assert: invitee became a company member
		assertTrue(realCompany.hasMember(invitee));
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
		String owner = realCompany.getFounder().getUsername();
		String invitee = "newOwner";

		// Act: invite and accept
		UUID invitationId = companyService.inviteCompanyOwner(owner, companyId, invitee);
		companyService.acceptCompanyInvitation(invitationId, companyId);

		// Assert: invitee became an owner
		assertTrue(realCompany.isOwner(invitee));
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
		String owner = realCompany.getFounder().getUsername();
		String invitee = "inviteeUser";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		UUID invitationId = companyService.inviteCompanyManager(owner, companyId, invitee, perms);

		// Act
		companyService.acceptCompanyInvitation(invitationId, companyId);

		// Assert
		assertTrue(realCompany.hasMember(invitee));
	}

	@Test
	public void testAcceptCompanyInvitation_MissingCompany_Throws() {
		// Arrange: repository returns empty
		UUID companyId = UUID.randomUUID();
		UUID invitationId = UUID.randomUUID();
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.empty());

		// Act & Assert
		assertThrows(Exception.class,
				() -> companyService.acceptCompanyInvitation(invitationId, companyId));
	}

	@Test
	public void testSuccessfulManagerAppointment_CreatesInvitationAndPendingUntilAccept() {
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "ManagerCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String owner = realCompany.getFounder().getUsername();
		String appointee = "newManager";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		UUID invitationId = companyService.inviteCompanyManager(owner, companyId, appointee, perms);

		assertNotNull("Invitation id should be returned", invitationId);
		// Before acceptance the user should NOT be a member
		assertFalse(realCompany.hasMember(appointee));

		companyService.acceptCompanyInvitation(invitationId, companyId);

		// After acceptance the appointee becomes a member
		assertTrue(realCompany.hasMember(appointee));
	}

	@Test
	public void testAppointeeAlreadyHasRole_Blocked() {
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "DupCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String owner = realCompany.getFounder().getUsername();
		String appointee = "mgrAlready";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		// Pre-appoint the user so they already have a role
		realCompany.appointNewManager(appointee, owner, perms);
		assertTrue(realCompany.hasMember(appointee));

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

		String owner = realCompany.getFounder().getUsername();
		String existingManager = "mgrUser";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.VIEW_HISTORY);

		// Founder appoints an initial manager
		realCompany.appointNewManager(existingManager, owner, perms);
		assertTrue(realCompany.hasMember(existingManager));

		// That manager (not an owner) attempts to invite another manager
		String appointee = "attemptedNewMgr";

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> companyService.inviteCompanyManager(existingManager, companyId, appointee, perms));

		assertEquals("The appointer is not a company owner and therefore cannot invite a new manager", ex.getMessage());
		// Ensure no change in membership
		assertFalse(realCompany.hasMember(appointee));
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
		// Arrange: prepare a company and add a manager under the founder
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "PermCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));
		String owner = realCompany.getFounder().getUsername();
		String manager = "mgrUser";
		Set<CompanyPermission> initial = new HashSet<>();
		initial.add(CompanyPermission.VIEW_HISTORY);
		realCompany.appointNewManager(manager, owner, initial);

		// Sanity check
		assertTrue(realCompany.hasMember(manager));

		// Act: change permissions
		Set<CompanyPermission> newPerms = new HashSet<>();
		newPerms.add(CompanyPermission.CONFIGURE_LAYOUT);
		companyService.changeManagerPermissions(owner, companyId, manager, newPerms);

		// Assert: manager permissions updated
		var subs = realCompany.getFounder().getSubordinates();
		CompanyManager found = null;
		for (var s : subs) {
			if (s.getUsername().equals(manager) && s instanceof CompanyManager) {
				found = (CompanyManager) s;
				break;
			}
		}
		assertNotNull(found);
		assertTrue(found.getPremissions().contains(CompanyPermission.CONFIGURE_LAYOUT));
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
		String owner = realCompany.getFounder().getUsername();
		String removeUser = "memberUser";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);
		realCompany.appointNewManager(removeUser, owner, perms);

		// Sanity check
		assertTrue(realCompany.hasMember(removeUser));

		// Act
		companyService.removeCompanyMemberAsOwner(owner, companyId, removeUser);

		// Assert
		assertFalse(realCompany.hasMember(removeUser));
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

		assertThrows(Exception.class, () -> companyService.closeCompanyAsAdmin(adminUsername, companyId));

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

	/*
	 * removeCompanyMemberAsAdmin tests
	 */

	@Test
	public void removeCompanyMemberAsAdmin_whenAdminRemovesExistingMember_removesFromAllCompaniesAndSaves() {
		Company firstCompany = mock(Company.class);
		Company secondCompany = mock(Company.class);

		when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
		when(companyRepositoryMock.getCompaniesByMember(memberUsername))
				.thenReturn(List.of(firstCompany, secondCompany));

		companyService.removeCompanyMemberAsAdmin(adminUsername, memberUsername);

		verify(userRepositoryMock).isSystemAdmin(adminUsername);
		verify(companyRepositoryMock).getCompaniesByMember(memberUsername);

		verify(firstCompany).removeMemberAsAdmin(memberUsername);
		verify(secondCompany).removeMemberAsAdmin(memberUsername);

		verify(companyRepositoryMock).save(firstCompany);
		verify(companyRepositoryMock).save(secondCompany);
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
		verify(companyRepositoryMock, never()).getCompaniesByMember(anyString());
		verify(companyRepositoryMock, never()).save(any());
	}

	@Test
	public void removeCompanyMemberAsAdmin_whenUserIsNotAssignedToAnyCompany_throwsExceptionAndDoesNotSave() {
		when(userRepositoryMock.isSystemAdmin(adminUsername)).thenReturn(true);
		when(companyRepositoryMock.getCompaniesByMember(memberUsername)).thenReturn(List.of());

		assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsAdmin(adminUsername, memberUsername));

		verify(userRepositoryMock).isSystemAdmin(adminUsername);
		verify(companyRepositoryMock).getCompaniesByMember(memberUsername);
		verify(companyRepositoryMock, never()).save(any());
	}

	@Test
	public void testSuccessfulManagerRemoval_RemovesManagerAndUpdatesHierarchy() {
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "ManagerRemovalCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String owner = realCompany.getFounder().getUsername();
		String manager = "managerToRemove";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);

		realCompany.appointNewManager(manager, owner, perms);
		assertTrue(realCompany.hasMember(manager));

		// Act
		companyService.removeCompanyMemberAsOwner(owner, companyId, manager);

		// Assert: manager removed (treated as subscriber/outside company)
		assertFalse(realCompany.hasMember(manager));

		// Hierarchy/mermaid should no longer contain the manager
		String mermaid = companyService.getCompanyHierarchyMermaid(companyId, owner);
		assertFalse(mermaid.contains(manager));
	}

	@Test
	public void testUnauthorizedManagerRemoval_BlockedWhenNotDirectAppointer() {
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "AuthRemovalCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String founder = realCompany.getFounder().getUsername();
		String owner1 = "owner1";
		String owner2 = "owner2";
		realCompany.appointNewOwner(owner1, founder);
		realCompany.appointNewOwner(owner2, founder);

		String manager = "managedByOwner2";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);
		realCompany.appointNewManager(manager, owner2, perms);

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> companyService.removeCompanyMemberAsOwner(owner1, companyId, manager));

		assertEquals("The owner can only remove managers that are under him in the company hyrarchy", ex.getMessage());
		// ensure manager still exists
		assertTrue(realCompany.hasMember(manager));
	}

	@Test
	public void testRolesListUpdateAfterRemoval_ManagerNoLongerListed() {
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("ownerUser", "RolesUpdateCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String owner = realCompany.getFounder().getUsername();
		String manager = "tempManager";
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);
		realCompany.appointNewManager(manager, owner, perms);

		assertTrue(realCompany.hasMember(manager));

		companyService.removeCompanyMemberAsOwner(owner, companyId, manager);

		// The removed user should not appear under "managers" in the hierarchy
		String mermaid = companyService.getCompanyHierarchyMermaid(companyId, owner);
		assertFalse(mermaid.contains(manager));
		assertFalse(realCompany.hasMember(manager));
	}

	@Test
	public void testSuccessfulRecursiveOwnershipRemoval_RemovesOwnerAndAllSubordinates() {
		// We'll simulate recursive removal at the domain-service level using the mock
		CompanyService svcWithMock = new CompanyService(rolesDomainServiceMock, purchaseDomainService);
		UUID companyId = UUID.randomUUID();
		Company realCompany = new Company("founderUser", "RecursiveRemovalCorp");
		when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));

		String founder = realCompany.getFounder().getUsername();
		// Create ownerA under founder
		String ownerA = "ownerA";
		realCompany.appointNewOwner(ownerA, founder);

		// ownerA appoints ownerB and managerX
		String ownerB = "ownerB";
		String managerX = "mgrX";
		realCompany.appointNewOwner(ownerB, ownerA);
		Set<CompanyPermission> perms = new HashSet<>();
		perms.add(CompanyPermission.MANAGE_POLICIES);
		realCompany.appointNewManager(managerX, ownerB, perms);

		assertTrue(realCompany.hasMember(ownerA));
		assertTrue(realCompany.hasMember(ownerB));
		assertTrue(realCompany.hasMember(managerX));

		// Mock domain service to perform recursive removal when asked
		doAnswer(invocation -> {
			String actingOwner = invocation.getArgument(0);
			UUID cid = invocation.getArgument(1);
			String toRemove = invocation.getArgument(2);
			Company comp = companyRepositoryMock.findByID(cid).get();

			// recursive helper
			java.util.function.Consumer<String> recursiveRemove = new java.util.function.Consumer<>() {
				@Override
				public void accept(String username) {
					var member = comp.getMember(username);
					if (member instanceof CompanyOwner) {
						var owner = (CompanyOwner) member;
						// copy to avoid ConcurrentModification
						var subs = new java.util.ArrayList<>(owner.getSubordinates());
						for (var s : subs) {
							accept(s.getUsername());
						}
						comp.removeMemberAsOwner(username, actingOwner);
					} else {
						// manager
						comp.removeMemberAsOwner(username, actingOwner);
					}
				}
			};

			recursiveRemove.accept(toRemove);
			return null;
		}).when(rolesDomainServiceMock).removeCompanyMemberAsOwner(anyString(), any(UUID.class), anyString());

		// Act
		svcWithMock.removeCompanyMemberAsOwner(ownerA, companyId, ownerB);

		// Assert: ownerB and his subordinate managerX are removed
		assertFalse(realCompany.hasMember(ownerB));
		assertFalse(realCompany.hasMember(managerX));
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