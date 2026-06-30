package org.example.AdditionalTests;

import org.example.ApplicationLayer.dto.CompanyDTOs.CompanyAccessResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.CompanyMembershipResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.InvitationResponse;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.PolicyManagment.DiscountType;
import org.example.DomainLayer.RolesDomainService;
import org.example.DomainLayer.UserAggregate.CompanyFounder;
import org.example.DomainLayer.UserAggregate.CompanyManager;
import org.example.DomainLayer.UserAggregate.CompanyOwner;
import org.example.DomainLayer.UserAggregate.User;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RolesDomainServiceAdditionalTests {

    private ICompanyRepository companyRepository;
    private IUserRepository userRepository;
    private RolesDomainService service;

    @Before
    public void setUp() {
        companyRepository = mock(ICompanyRepository.class);
        userRepository = mock(IUserRepository.class);
        service = new RolesDomainService(companyRepository, userRepository);
    }

    private Company company(String founderEmail) {
        return new Company(founderEmail, "Acme", DiscountType.ALL);
    }

    private User founder(UUID companyId, String email) {
        User user = new User(UUID.randomUUID(), "founder", email, "hash", 40);
        user.getCompanyRoles().put(companyId, new CompanyFounder(email));
        return user;
    }

    @Test
    public void companyLookups_validateNullMissingAndReturnExistingCompany() {
        UUID companyId = UUID.randomUUID();
        Company company = company("founder@example.com");

        assertThrows(IllegalArgumentException.class, () -> service.getCompany(null));
        assertThrows(IllegalArgumentException.class, () -> service.getCompanyOwner(null));

        when(companyRepository.findByID(companyId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getCompany(companyId));

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));

        assertSame(company, service.getCompany(companyId));
        assertEquals("founder@example.com", service.getCompanyOwner(companyId));
    }

    @Test
    public void removeCompanyMemberAsAdmin_coversValidationFailuresAndSuccess() {
        UUID companyId = UUID.randomUUID();
        Company company = company("founder@example.com");
        User user = mock(User.class);

        assertThrows(IllegalArgumentException.class,
                () -> service.removeCompanyMemberAsAdmin(null, "member@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service.removeCompanyMemberAsAdmin("admin", "   "));

        when(userRepository.isSystemAdmin("not-admin")).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> service.removeCompanyMemberAsAdmin("not-admin", "member@example.com"));

        when(userRepository.isSystemAdmin("admin")).thenReturn(true);
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.removeCompanyMemberAsAdmin("admin", "missing@example.com"));

        when(userRepository.findByEmail("member@example.com")).thenReturn(Optional.of(user));
        when(userRepository.getCompaniesIdsByMember("member@example.com")).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> service.removeCompanyMemberAsAdmin("admin", "member@example.com"));

        when(userRepository.getCompaniesIdsByMember("member@example.com")).thenReturn(List.of(companyId));
        when(companyRepository.findByID(companyId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.removeCompanyMemberAsAdmin("admin", "member@example.com"));

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));

        service.removeCompanyMemberAsAdmin("admin", "member@example.com");

        verify(user).removeFromCompanyAsAdmin(companyId);
        verify(userRepository).add(user);
    }

    @Test
    public void inviteAcceptRejectAndInvitationResponses_coverManagerAndOwnerFlows() {
        UUID companyId = UUID.randomUUID();
        Company company = company("owner@example.com");
        User owner = founder(companyId, "owner@example.com");
        User managerCandidate = new User(UUID.randomUUID(), "manager", "manager@example.com", "hash", 30);
        User ownerCandidate = new User(UUID.randomUUID(), "owner2", "owner2@example.com", "hash", 32);

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(managerCandidate));
        when(userRepository.findByEmail("owner2@example.com")).thenReturn(Optional.of(ownerCandidate));

        assertThrows(IllegalArgumentException.class,
                () -> service.inviteCompanyManager(null, companyId, "manager@example.com", Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> service.inviteCompanyOwner("owner@example.com", companyId, " "));

        UUID managerInvitationId = service.inviteCompanyManager(
                "owner@example.com",
                companyId,
                "manager@example.com",
                Set.of(CompanyPermission.MANAGE_POLICIES)
        );

        assertEquals(1, managerCandidate.getCompanyInvitations().size());
        verify(userRepository).add(managerCandidate);

        List<InvitationResponse> invitationResponses = service.getUserInvitations("manager@example.com");
        assertEquals(1, invitationResponses.size());
        assertNotNull(invitationResponses.get(0));

        service.acceptCompanyInvitation(managerInvitationId, "manager@example.com", companyId);

        assertTrue(managerCandidate.isCompanyMember(companyId));
        assertTrue(managerCandidate.isManagerInCompany(companyId));

        UUID ownerInvitationId = service.inviteCompanyOwner(
                "owner@example.com",
                companyId,
                "owner2@example.com"
        );

        assertEquals(1, ownerCandidate.getCompanyInvitations().size());

        service.rejectCompanyInvitation(ownerInvitationId, "owner2@example.com", companyId);

        assertTrue(ownerCandidate.getCompanyInvitations().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> service.getUserInvitations(" "));

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.getUserInvitations("missing@example.com"));
    }

    @Test
    public void acceptCompanyInvitation_cancelsOtherPendingInvitationsToSameCompany() {
        UUID companyId = UUID.randomUUID();
        Company company = company("owner@example.com");
        User owner = founder(companyId, "owner@example.com");
        User candidate = new User(UUID.randomUUID(), "candidate", "candidate@example.com", "hash", 28);

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));
        when(userRepository.findByEmail("candidate@example.com")).thenReturn(Optional.of(candidate));

        // The same user receives two pending invitations to the same company
        // (e.g. invited by two different owners).
        UUID firstInvitationId = service.inviteCompanyManager(
                "owner@example.com", companyId, "candidate@example.com", Set.of());
        service.inviteCompanyManager(
                "owner@example.com", companyId, "candidate@example.com", Set.of());

        assertEquals(2, candidate.getCompanyInvitations().size());

        // Accepting one invitation must cancel the dangling sibling.
        service.acceptCompanyInvitation(firstInvitationId, "candidate@example.com", companyId);

        assertTrue(candidate.isCompanyMember(companyId));
        assertTrue(candidate.getCompanyInvitations().isEmpty());
        assertTrue(service.getUserInvitations("candidate@example.com").isEmpty());
    }

    @Test
    public void inviteCompanyManager_rejectsNonOwnerBeforeLookingUpInvitee() {
        UUID companyId = UUID.randomUUID();
        Company company = company("founder@example.com");
        User plainUser = new User(UUID.randomUUID(), "plain", "plain@example.com", "hash", 25);

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail("plain@example.com")).thenReturn(Optional.of(plainUser));

        assertThrows(IllegalArgumentException.class,
                () -> service.inviteCompanyManager(
                        "plain@example.com",
                        companyId,
                        "missing-invitee@example.com",
                        Set.of(CompanyPermission.MANAGE_POLICIES)
                ));

        verify(userRepository, never()).findByEmail("missing-invitee@example.com");
    }

    @Test
    public void policyAndDiscountChanges_allowFounderAndRejectPlainMember() {
        UUID companyId = UUID.randomUUID();
        Company company = company("founder@example.com");
        User founder = founder(companyId, "founder@example.com");
        User plain = new User(UUID.randomUUID(), "plain", "plain@example.com", "hash", 20);

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail("founder@example.com")).thenReturn(Optional.of(founder));
        when(userRepository.findByEmail("plain@example.com")).thenReturn(Optional.of(plain));

        service.addPurchasePolicy(
                "founder@example.com",
                companyId,
                Optional.of(18f),
                Optional.of(1),
                Optional.of(4),
                Optional.of(false),
                true
        );
        assertNotNull(company.getPurchasePolicy().getRulesView());

        UUID purchaseRuleId = company.getPurchasePolicy().getRulesView().getId();
        service.deletePurchasePolicy("founder@example.com", companyId, purchaseRuleId);

        service.addOvertDiscount(
                "founder@example.com",
                companyId,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                10f
        );
        service.addConditionalDiscount(
                "founder@example.com",
                companyId,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                20f,
                2,
                1
        );
        service.addCouponCode(
                "founder@example.com",
                companyId,
                LocalDate.now().minusDays(1),
                LocalDate.now().plusDays(1),
                15f,
                "SAVE15"
        );

        assertEquals(3, company.getDiscountPolicy().getDiscountRules().size());

        UUID discountId = company.getDiscountPolicy().getDiscountRules().get(0).getId();
        service.removeDiscount("founder@example.com", companyId, discountId);
        assertEquals(2, company.getDiscountPolicy().getDiscountRules().size());

        assertThrows(IllegalArgumentException.class,
                () -> service.addPurchasePolicy(
                        "plain@example.com",
                        companyId,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        true
                ));

        assertThrows(IllegalArgumentException.class,
                () -> service.removeDiscount("plain@example.com", companyId, discountId));
    }

    @Test
    public void managerPermissionChangeRateCompanyHierarchyMembershipAndAccessViews() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID ratingUserId = UUID.randomUUID();
        Company company = company("founder@example.com");

        User founder = founder(companyId, "founder@example.com");
        CompanyFounder founderRole = (CompanyFounder) founder.getCompanyRole(companyId);

        User manager = new User(UUID.randomUUID(), "manager", "manager@example.com", "hash", 30);
        CompanyManager managerRole = new CompanyManager(
                "manager@example.com",
                founderRole,
                Set.of(CompanyPermission.VIEW_HISTORY)
        );
        managerRole.getEventsIds().add(eventId);
        founderRole.addSubordinate(managerRole);
        manager.getCompanyRoles().put(companyId, managerRole);

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail("founder@example.com")).thenReturn(Optional.of(founder));
        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(manager));
        when(userRepository.getCompaniesIdsByMember("manager@example.com")).thenReturn(List.of(companyId));

        service.changeManagerPermissions(
                "founder@example.com",
                companyId,
                "manager@example.com",
                Set.of(CompanyPermission.MANAGE_POLICIES)
        );

        assertTrue(manager.hasPremisions(companyId, CompanyPermission.MANAGE_POLICIES, eventId));
        verify(userRepository).add(manager);

        assertThrows(DomainException.class,
                () -> service.rateCompany(null, companyId, 5));

        service.rateCompany(ratingUserId, companyId, 5);
        verify(companyRepository).save(company);

        String mermaid = service.getCompanyHierarchyMermaid(companyId, "founder@example.com");
        assertTrue(mermaid.contains("Founder"));

        List<CompanyMembershipResponse> memberships = service.getUserCompanies("manager@example.com");
        assertEquals(1, memberships.size());

        CompanyAccessResponse access = service.getCompanyAccess(companyId, "manager@example.com");
        assertNotNull(access);

        assertThrows(IllegalArgumentException.class, () -> service.getUserCompanies(" "));
        assertThrows(IllegalArgumentException.class, () -> service.getCompanyAccess(null, "manager@example.com"));
        assertThrows(IllegalArgumentException.class, () -> service.getCompanyAccess(companyId, " "));

        User outsider = new User(UUID.randomUUID(), "outsider", "outsider@example.com", "hash", 22);
        when(userRepository.findByEmail("outsider@example.com")).thenReturn(Optional.of(outsider));
        assertThrows(IllegalArgumentException.class,
                () -> service.getCompanyAccess(companyId, "outsider@example.com"));
    }

    @Test
    public void removeCompanyMemberAsOwner_andOwnerSubordinateLookups_coverValidationAndSuccess() {
        UUID companyId = UUID.randomUUID();
        Company company = company("founder@example.com");
        User founder = founder(companyId, "founder@example.com");
        CompanyFounder founderRole = (CompanyFounder) founder.getCompanyRole(companyId);

        User ownerToRemove = new User(UUID.randomUUID(), "owner", "owner@example.com", "hash", 35);
        CompanyOwner ownerRole = new CompanyOwner("owner@example.com", founderRole);
        founderRole.addSubordinate(ownerRole);
        ownerToRemove.getCompanyRoles().put(companyId, ownerRole);

        when(companyRepository.findByID(companyId)).thenReturn(Optional.of(company));
        when(userRepository.findByEmail("founder@example.com")).thenReturn(Optional.of(founder));
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(ownerToRemove));
        when(userRepository.getOwnerAndSubordinatesUsernames(companyId, "founder@example.com"))
                .thenReturn(List.of("founder@example.com", "owner@example.com"));

        assertThrows(IllegalArgumentException.class,
                () -> service.removeCompanyMemberAsOwner(null, companyId, "owner@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service.removeCompanyMemberAsOwner("founder@example.com", companyId, " "));

        service.removeCompanyMemberAsOwner("founder@example.com", companyId, "owner@example.com");

        assertFalse(ownerToRemove.isCompanyMember(companyId));
        verify(userRepository).add(ownerToRemove);
        verify(userRepository).add(founder);

        List<String> visible = service.getOwnerAndSubordinatesUsernames(companyId, "founder@example.com");
        assertEquals(List.of("founder@example.com", "owner@example.com"), visible);

        assertThrows(IllegalArgumentException.class,
                () -> service.getOwnerAndSubordinatesUsernames(null, "founder@example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> service.getOwnerAndSubordinatesUsernames(companyId, " "));

        User plain = new User(UUID.randomUUID(), "plain", "plain@example.com", "hash", 20);
        when(userRepository.findByEmail("plain@example.com")).thenReturn(Optional.of(plain));
        assertThrows(IllegalArgumentException.class,
                () -> service.getOwnerAndSubordinatesUsernames(companyId, "plain@example.com"));
    }
}
