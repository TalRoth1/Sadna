package org.example.ApplicationLayer;

import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.RolesDomainService;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyManager;
import org.example.DomainLayer.PolicyAggregate.AgeRule;
import org.example.DomainLayer.PolicyAggregate.LoneSeatRule;
import org.example.DomainLayer.PolicyAggregate.OvertDiscount;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CompanyServiceTest {

        @Mock
        private RolesDomainService rolesDomainServiceMock;

        @Mock
        private ICompanyRepository companyRepositoryMock;

        @Mock
        private IUserRepository userRepositoryMock;

        private CompanyService companyService;
        private RolesDomainService rolesDomainService;

        @Before
        public void setUp() {
                rolesDomainServiceMock = mock(RolesDomainService.class);
                userRepositoryMock = mock(IUserRepository.class);
                rolesDomainService = new RolesDomainService(companyRepositoryMock, userRepositoryMock);
                companyService = new CompanyService(rolesDomainService);
        }

        /* Test cases for closeCompanyAsAdmin method */
        @Test
        public void testSuccessfulCompanyClosure() {
                String adminUsername = "admin";
                UUID companyId = UUID.randomUUID();

                companyService.closeCompanyAsAdmin(adminUsername, companyId);

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

                assertThrows(IllegalArgumentException.class,
                                () -> companyService.closeCompanyAsAdmin(adminUsername, companyId));
        }

        @Test
        public void testCompanyAlreadyClosed() {
                String adminUsername = "admin";
                UUID companyId = UUID.randomUUID();

                doThrow(new IllegalArgumentException("Company is already closed"))
                                .when(rolesDomainServiceMock)
                                .closeCompanyAsAdmin(adminUsername, companyId);

                assertThrows(IllegalArgumentException.class,
                                () -> companyService.closeCompanyAsAdmin(adminUsername, companyId));
        }

        @Test
        public void testUnauthorizedCompanyClosure() {
                String username = "regularUser";
                UUID companyId = UUID.randomUUID();

                doThrow(new IllegalArgumentException("User is not an admin"))
                                .when(rolesDomainServiceMock)
                                .closeCompanyAsAdmin(username, companyId);

                assertThrows(IllegalArgumentException.class,
                                () -> companyService.closeCompanyAsAdmin(username, companyId));
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
        public void testSuccessfulUserRemoval() {
                String adminUsername = "admin";
                String usernameToRemove = "member";

                companyService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

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

                assertThrows(IllegalArgumentException.class,
                                () -> companyService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove));
        }

        @Test
        public void testUserIsNotMember() {
                String adminUsername = "admin";
                String usernameToRemove = "guest";

                doThrow(new IllegalArgumentException("User is not a member"))
                                .when(rolesDomainServiceMock)
                                .removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

                assertThrows(IllegalArgumentException.class,
                                () -> companyService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove));
        }

        @Test
        public void testUnauthorizedUserRemoval() {
                String adminUsername = "regularUser";
                String usernameToRemove = "member";

                doThrow(new IllegalArgumentException("User is not system admin"))
                                .when(rolesDomainServiceMock)
                                .removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

                assertThrows(IllegalArgumentException.class,
                                () -> companyService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove));
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
                companyService.addPolicyRule(companyId, ageLimit, Optional.empty(), Optional.empty(), Optional.empty());

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
                companyService.addPolicyRule(companyId, Optional.of(18.0f), Optional.empty(), Optional.empty(),
                                Optional.empty());
                companyService.addPolicyRule(companyId, Optional.of(21.0f), Optional.empty(), Optional.empty(),
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
                companyService.addOvertDiscount(companyId, startDate, endDate, discountAmount);

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
                companyService.deletePolicyRule(companyId, true, false, false, false);

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
                companyService.addPolicyRule(companyId, Optional.empty(), Optional.empty(), Optional.empty(),
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
                companyService.addOvertDiscount(companyId, LocalDate.now(), LocalDate.now().plusDays(5), 10.0f);
                companyService.addOvertDiscount(companyId, LocalDate.now(), LocalDate.now().plusDays(10), 20.0f);

                // 2. Retrieve the rules to get the specific ID of the first one
                var discounts = realCompany.getDiscountPolicy().gDiscountRules();
                assertEquals("Should have 2 discounts initially", 2, discounts.size());
                UUID firstDiscountId = discounts.get(0).getId();

                // Act
                // Assuming your CompanyService has a deleteDiscount method that takes the ID
                // If your service uses a different signature, adjust accordingly:
                rolesDomainService.removeDiscount(companyId, firstDiscountId);

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
                CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock);

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
                CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock);

                // Act
                serviceWithMock.createCompany(null, "Some Company");

                // Assert: Handled by expected = IllegalArgumentException.class
        }

        @Test(expected = IllegalArgumentException.class)
        public void testCreateCompany_EmptyUsername_ThrowsException() {
                // Arrange
                CompanyService serviceWithMock = new CompanyService(rolesDomainServiceMock);

                // Act
                serviceWithMock.createCompany("   ", "Some Company");

                // Assert: Handled by expected = IllegalArgumentException.class
        }

        /* Tests for manager/owner invitations and permissions delegation */
        @Test
        public void testInviteCompanyManager_Valid_CallsDomainService() {
                // Arrange: real company + repository
                UUID companyId = UUID.randomUUID();
                Company realCompany = new Company("ownerUser", "TestCorp");
                when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));
                String owner = realCompany.getFounder().getUsername();
                String invitee = "managerUser";
                Set<CompanyPermission> perms = new HashSet<>();
                perms.add(CompanyPermission.MANAGE_POLICIES);

                // Act: invite then accept the invitation
                UUID invitationId = companyService.inviteCompanyManager(owner, companyId, invitee, perms);
                companyService.acceptCompanyInvitation(invitationId, companyId);

                // Assert: invitee became a company member
                assertTrue(realCompany.hasMember(invitee));
        }

        @Test
        public void testInviteCompanyManager_OwnerUsernameIsNull() {
                // Arrange
                UUID companyId = UUID.randomUUID();
                Set<CompanyPermission> perms = new HashSet<>();
                perms.add(CompanyPermission.MANAGE_POLICIES);

                // Act & Assert: should throw for null owner
                assertThrows(IllegalArgumentException.class,
                                () -> companyService.inviteCompanyManager(null, companyId, "m", perms));

                // Assert: repository not queried
                verifyNoInteractions(companyRepositoryMock);
        }

        @Test
        public void testInviteCompanyOwner_Valid_CallsDomainService() {
                // Arrange: prepare company and repository
                UUID companyId = UUID.randomUUID();
                Company realCompany = new Company("ownerUser", "OwnerCorp");
                when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));
                String owner = realCompany.getFounder().getUsername();
                String invitee = "newOwner";

                // Act: invite + accept
                UUID invitationId = companyService.inviteCompanyOwner(owner, companyId, invitee);
                companyService.acceptCompanyInvitation(invitationId, companyId);

                // Assert: invitee became an owner
                assertTrue(realCompany.isOwner(invitee));
        }

        @Test
        public void testInviteCompanyOwner_OwnerUsernameIsBlank() {
                // Arrange
                UUID companyId = UUID.randomUUID();

                // Act & Assert: blank owner should cause validation failure
                assertThrows(IllegalArgumentException.class,
                                () -> companyService.inviteCompanyOwner(" ", companyId, "someone"));

                // Assert: repository not queried
                verifyNoInteractions(companyRepositoryMock);
        }

        @Test
        public void testAcceptCompanyInvitation_Valid_DelegatesToDomainService() {
                // Arrange: create a real company and an invitation via service
                UUID companyId = UUID.randomUUID();
                Company realCompany = new Company("ownerUser", "InviteCorp");
                when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.of(realCompany));
                String owner = realCompany.getFounder().getUsername();
                String invitee = "inviteeUser";
                Set<CompanyPermission> perms = new HashSet<>();
                perms.add(CompanyPermission.MANAGE_POLICIES);

                UUID invitationId = companyService.inviteCompanyManager(owner, companyId, invitee, perms);

                // Act: accept the invitation
                companyService.acceptCompanyInvitation(invitationId, companyId);

                // Assert: the invitee is now a member
                assertTrue(realCompany.hasMember(invitee));
        }

        @Test
        public void testAcceptCompanyInvitation_DomainThrowsException_Propagates() {
                // Arrange: repository returns empty (company missing)
                UUID companyId = UUID.randomUUID();
                UUID invitationId = UUID.randomUUID();
                when(companyRepositoryMock.findByID(companyId)).thenReturn(Optional.empty());

                // Act & Assert: missing company causes exception from domain layer
                assertThrows(Exception.class,
                                () -> companyService.acceptCompanyInvitation(invitationId, companyId));
        }

        @Test
        public void testChangeManagerPermissions_Valid_CallsDomainService() {
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

                // Assert: manager permissions updated (found under founder's subordinates)
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
        public void testChangeManagerPermissions_OwnerNull_Throws() {
                // Arrange
                UUID companyId = UUID.randomUUID();

                // Act & Assert: null owner should cause validation exception
                assertThrows(IllegalArgumentException.class,
                                () -> companyService.changeManagerPermissions(null, companyId, "m", new HashSet<>()));

                // Assert: repository not queried
                verifyNoInteractions(companyRepositoryMock);
        }

        @Test
        public void testChangeManagerPermissions_ManagerBlank_Throws() {
                // Arrange
                UUID companyId = UUID.randomUUID();

                // Act & Assert: blank manager username is invalid
                assertThrows(IllegalArgumentException.class, () -> companyService.changeManagerPermissions("owner",
                                companyId, " ", new HashSet<>()));

                // Assert: repository not queried
                verifyNoInteractions(companyRepositoryMock);
        }

        @Test
        public void testRemoveCompanyMemberAsOwner_Valid_CallsDomainService() {
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

                        // Assert: member removed from company
                        assertFalse(realCompany.hasMember(removeUser));
        }

        @Test
        public void testRemoveCompanyMemberAsOwner_OwnerBlank_Throws() {
                        // Arrange
                        UUID companyId = UUID.randomUUID();

                        // Act & Assert: blank owner should fail validation
                        assertThrows(IllegalArgumentException.class,
                                        () -> companyService.removeCompanyMemberAsOwner(" ", companyId, "u"));

                        // Assert: repository not queried
                        verifyNoInteractions(companyRepositoryMock);
        }

}