package org.example.AdditionalTests;

import org.example.DomainLayer.AdminAggregate.Admin;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.UserAggregate.CompanyFounder;
import org.example.DomainLayer.UserAggregate.CompanyManager;
import org.example.DomainLayer.UserAggregate.CompanyOwner;
import org.example.DomainLayer.UserAggregate.User;
import org.example.InfrastructureLayer.UserRepository;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UserRepositoryAdditionalTests {

    @Test
    public void ownerAndSubordinates_handlesNullMissingNonMemberAndRecursiveHierarchy() {
        UserRepository repo = new UserRepository();
        UUID companyId = UUID.randomUUID();

        assertEquals(List.of(), repo.getOwnerAndSubordinatesUsernames(null, "owner@example.com"));
        assertEquals(List.of(), repo.getOwnerAndSubordinatesUsernames(companyId, null));
        assertEquals(List.of(), repo.getOwnerAndSubordinatesUsernames(companyId, "missing@example.com"));

        User nonMember = new User(UUID.randomUUID(), "non", "non@example.com", "hash", 22);
        repo.add(nonMember);
        assertEquals(List.of(), repo.getOwnerAndSubordinatesUsernames(companyId, "non@example.com"));

        User founderUser = new User(UUID.randomUUID(), "founder", "founder@example.com", "hash", 40);
        User ownerUser = new User(UUID.randomUUID(), "owner", "owner@example.com", "hash", 35);
        User managerUser = new User(UUID.randomUUID(), "manager", "manager@example.com", "hash", 30);

        CompanyFounder founder = new CompanyFounder("founder@example.com");
        CompanyOwner owner = new CompanyOwner("owner@example.com", founder);
        CompanyManager manager = new CompanyManager(
                "manager@example.com",
                owner,
                Set.of(CompanyPermission.MANAGE_INVENTORY)
        );

        founder.addSubordinate(owner);
        owner.addSubordinate(manager);

        founderUser.getCompanyRoles().put(companyId, founder);
        ownerUser.getCompanyRoles().put(companyId, owner);
        managerUser.getCompanyRoles().put(companyId, manager);

        repo.add(founderUser);
        repo.add(ownerUser);
        repo.add(managerUser);

        assertEquals(
                List.of("founder@example.com", "owner@example.com", "manager@example.com"),
                repo.getOwnerAndSubordinatesUsernames(companyId, "founder@example.com")
        );
        assertEquals(
                List.of("owner@example.com", "manager@example.com"),
                repo.getOwnerAndSubordinatesUsernames(companyId, "owner@example.com")
        );
    }

    @Test
    public void adminUsernamesAndSystemAdmin_handleNullAdminUsernameAndUserBackedAdmin() {
        UserRepository repo = new UserRepository();
        UUID userBackedAdminId = UUID.randomUUID();
        UUID nullUsernameAdminId = UUID.randomUUID();

        Admin nullUsernameAdmin = mock(Admin.class);
        when(nullUsernameAdmin.getId()).thenReturn(nullUsernameAdminId);
        when(nullUsernameAdmin.getUsername()).thenReturn(null);
        repo.addAdmin(nullUsernameAdmin);

        Admin userBackedAdmin = mock(Admin.class);
        when(userBackedAdmin.getId()).thenReturn(userBackedAdminId);
        when(userBackedAdmin.getUsername()).thenReturn("admin-alias");
        repo.addAdmin(userBackedAdmin);

        User adminUser = new User(userBackedAdminId, "real-admin", "admin@example.com", "hash", 40);
        repo.add(adminUser);

        assertEquals(Set.of("admin-alias"), repo.getAllAdminUsernames());
        assertTrue(repo.isSystemAdmin("admin-alias"));
        assertTrue(repo.isSystemAdmin("real-admin"));
        assertTrue(repo.isSystemAdmin("admin@example.com"));
        assertFalse(repo.isSystemAdmin("missing"));
    }

    @Test
    public void countCompanyMembersByRole_normalizesRoleNamesAndSkipsNonMembers() {
        UserRepository repo = new UserRepository();
        UUID companyId = UUID.randomUUID();
        UUID otherCompanyId = UUID.randomUUID();

        User founderUser = new User(UUID.randomUUID(), "founder", "founder@example.com", "hash", 40);
        founderUser.getCompanyRoles().put(companyId, new CompanyFounder("founder@example.com"));

        CompanyOwner ownerRole = mock(CompanyOwner.class);
        when(ownerRole.getRoleName()).thenReturn(" owner ");
        User ownerUser = new User(UUID.randomUUID(), "owner", "owner@example.com", "hash", 40);
        ownerUser.getCompanyRoles().put(companyId, ownerRole);

        CompanyManager managerRole = mock(CompanyManager.class);
        when(managerRole.getRoleName()).thenReturn(null);
        User managerUser = new User(UUID.randomUUID(), "manager", "manager@example.com", "hash", 40);
        managerUser.getCompanyRoles().put(companyId, managerRole);

        User otherCompanyUser = new User(UUID.randomUUID(), "other", "other@example.com", "hash", 40);
        otherCompanyUser.getCompanyRoles().put(otherCompanyId, new CompanyFounder("other@example.com"));

        repo.add(founderUser);
        repo.add(ownerUser);
        repo.add(managerUser);
        repo.add(otherCompanyUser);

        Map<String, Long> counts = repo.countCompanyMembersByRole(companyId);

        assertEquals(Long.valueOf(1), counts.get("FOUNDER"));
        assertEquals(Long.valueOf(1), counts.get("OWNER"));
        assertEquals(Long.valueOf(1), counts.get(""));
        assertFalse(counts.containsKey("MANAGER"));
    }
}
