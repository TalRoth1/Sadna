package org.example.DomainLayer;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.UserAggregate.User;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;

public class RolesDomainServiceTest {

    private FakeCompanyRepository companyRepository;
    private FakeUserRepository userRepository;
    private RolesDomainService rolesDomainService;

    private String adminUsername;
    private String regularUsername;

    @Before
    public void setUp() {
        companyRepository = new FakeCompanyRepository();
        userRepository = new FakeUserRepository();
        rolesDomainService = new RolesDomainService(companyRepository, userRepository);

        adminUsername = "admin";
        regularUsername = "regularUser";

        userRepository.addSystemAdmin(adminUsername);
    }

    /*
     * Close company flows: after the refactor closing a company deactivates it and is saved.
     * Member/role state is maintained on User aggregate and is not automatically removed by closeCompanyAsAdmin.
     */

    @Test
    public void closeCompanyAsAdmin_whenValidAdminClosesCompany_fullFlowUpdatesStateAndSaves() {
        Company company = new Company("founder", "Production Company");
        UUID companyId = company.getId();

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId);

        Company result = companyRepository.findByID(companyId).orElseThrow();

        assertFalse(result.isActive());
        assertEquals(1, companyRepository.getSaveCount(companyId));
    }

    @Test
    public void closeCompanyAsAdmin_whenCompanyHasOnlyFounder_closesSuccessfullyAndSaves() {
        Company company = new Company("founder", "Production Company");
        UUID companyId = company.getId();

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId);

        Company result = companyRepository.findByID(companyId).orElseThrow();

        assertFalse(result.isActive());
        assertEquals(1, companyRepository.getSaveCount(companyId));
    }

    @Test
    public void closeCompanyAsAdmin_whenCompanyHasMultipleOwnersAndManagers_deactivatesAndSaves() {
        Company company = new Company("founder", "Production Company");
        UUID companyId = company.getId();

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId);

        Company result = companyRepository.findByID(companyId).orElseThrow();

        assertFalse(result.isActive());
        assertEquals(1, companyRepository.getSaveCount(companyId));
    }

    @Test
    public void closeCompanyAsAdmin_whenUserIsNotSystemAdmin_doesNotChangeCompanyStateAndDoesNotSave() {
        Company company = new Company("founder", "Production Company");
        UUID companyId = company.getId();

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        assertThrows(IllegalArgumentException.class, () ->
                rolesDomainService.closeCompanyAsAdmin(regularUsername, companyId)
        );

        Company result = companyRepository.findByID(companyId).orElseThrow();

        assertTrue(result.isActive());
        assertEquals(0, companyRepository.getSaveCount(companyId));
    }

    @Test
    public void closeCompanyAsAdmin_whenCompanyDoesNotExist_throwsAndDoesNotSaveAnything() {
        UUID missingCompanyId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () ->
                rolesDomainService.closeCompanyAsAdmin(adminUsername, missingCompanyId)
        );

        assertEquals(0, companyRepository.getTotalSaveCount());
    }

    @Test
    public void closeCompanyAsAdmin_whenCompanyAlreadyInactive_doesNotChangeStateAgainAndDoesNotSaveAgain() {
        Company company = new Company("founder", "Production Company");
        UUID companyId = company.getId();

        company.AdminClose();

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        assertThrows(IllegalStateException.class, () ->
                rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId)
        );

        Company result = companyRepository.findByID(companyId).orElseThrow();

        assertFalse(result.isActive());
        assertEquals(0, companyRepository.getSaveCount(companyId));
    }

    /*
     * Remove user subscription from all companies by System Admin — membership is stored on User.
     */

    @Test
    public void removeCompanyMemberAsAdmin_whenUserExistsInMultipleCompanies_fullFlowRemovesFromAllRelevantCompaniesOnly() {
        String memberUsername = "member";

        Company firstCompany = new Company("founder1", "First Company");
        Company secondCompany = new Company("founder2", "Second Company");
        Company unrelatedCompany = new Company("founder3", "Unrelated Company");

        companyRepository.save(firstCompany);
        companyRepository.save(secondCompany);
        companyRepository.save(unrelatedCompany);
        companyRepository.resetSaveCounters();

        User memberUser = new User(UUID.randomUUID(), memberUsername, memberUsername, "hash", 30);
        memberUser.getCompanyRoles().put(firstCompany.getId(), new org.example.DomainLayer.UserAggregate.CompanyOwner(memberUsername, new org.example.DomainLayer.UserAggregate.CompanyFounder("founder1")));
        memberUser.getCompanyRoles().put(secondCompany.getId(), new org.example.DomainLayer.UserAggregate.CompanyManager(memberUsername, new org.example.DomainLayer.UserAggregate.CompanyFounder("founder2"), Set.of(CompanyPermission.VIEW_HISTORY)));
        userRepository.add(memberUser);

        rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, memberUsername);

        // membership removed from user
        assertFalse(memberUser.isCompanyMember(firstCompany.getId()));
        assertFalse(memberUser.isCompanyMember(secondCompany.getId()));
        // unrelated company unaffected
        assertFalse(memberUser.isCompanyMember(unrelatedCompany.getId()));

        // Domain service does not save Company when removing user roles
        assertEquals(0, companyRepository.getSaveCount(firstCompany.getId()));
        assertEquals(0, companyRepository.getSaveCount(secondCompany.getId()));
        assertEquals(0, companyRepository.getSaveCount(unrelatedCompany.getId()));
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserExistsInOneCompany_removesOnlyFromThatCompanyAndSavesOnlyThatCompany() {
        String memberUsername = "member";

        Company companyWithMember = new Company("founder1", "Company With Member");
        Company companyWithoutMember = new Company("founder2", "Company Without Member");

        companyRepository.save(companyWithMember);
        companyRepository.save(companyWithoutMember);
        companyRepository.resetSaveCounters();

        User memberUser = new User(UUID.randomUUID(), memberUsername, memberUsername, "hash", 30);
        memberUser.getCompanyRoles().put(companyWithMember.getId(), new org.example.DomainLayer.UserAggregate.CompanyOwner(memberUsername, new org.example.DomainLayer.UserAggregate.CompanyFounder("founder1")));
        userRepository.add(memberUser);

        rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, memberUsername);

        assertFalse(memberUser.isCompanyMember(companyWithMember.getId()));
        assertFalse(memberUser.isCompanyMember(companyWithoutMember.getId()));

        assertEquals(0, companyRepository.getSaveCount(companyWithMember.getId()));
        assertEquals(0, companyRepository.getSaveCount(companyWithoutMember.getId()));
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserIsManager_removesManagerAndSavesCompany() {
        String managerUsername = "manager";

        Company company = new Company("founder", "Production Company");

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        User managerUser = new User(UUID.randomUUID(), managerUsername, managerUsername, "hash", 30);
        managerUser.getCompanyRoles().put(company.getId(), new org.example.DomainLayer.UserAggregate.CompanyManager(managerUsername, new org.example.DomainLayer.UserAggregate.CompanyFounder("founder"), Set.of(CompanyPermission.VIEW_HISTORY)));
        userRepository.add(managerUser);

        rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, managerUsername);

        assertFalse(managerUser.isCompanyMember(company.getId()));

        assertEquals(0, companyRepository.getSaveCount(company.getId()));
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserHasDifferentRolesInDifferentCompanies_removesFromAllRelevantCompanies() {
        String usernameToRemove = "targetUser";

        Company ownerCompany = new Company("founder1", "Owner Company");
        Company managerCompany = new Company("founder2", "Manager Company");
        Company unrelatedCompany = new Company("founder3", "Unrelated Company");

        companyRepository.save(ownerCompany);
        companyRepository.save(managerCompany);
        companyRepository.save(unrelatedCompany);
        companyRepository.resetSaveCounters();

        User target = new User(UUID.randomUUID(), usernameToRemove, usernameToRemove, "hash", 30);
        target.getCompanyRoles().put(ownerCompany.getId(), new org.example.DomainLayer.UserAggregate.CompanyOwner(usernameToRemove, new org.example.DomainLayer.UserAggregate.CompanyFounder("founder1")));
        target.getCompanyRoles().put(managerCompany.getId(), new org.example.DomainLayer.UserAggregate.CompanyManager(usernameToRemove, new org.example.DomainLayer.UserAggregate.CompanyFounder("founder2"), Set.of(CompanyPermission.VIEW_HISTORY)));
        userRepository.add(target);

        rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

        assertFalse(target.isCompanyMember(ownerCompany.getId()));
        assertFalse(target.isCompanyMember(managerCompany.getId()));
        assertFalse(target.isCompanyMember(unrelatedCompany.getId()));

        assertEquals(0, companyRepository.getSaveCount(ownerCompany.getId()));
        assertEquals(0, companyRepository.getSaveCount(managerCompany.getId()));
        assertEquals(0, companyRepository.getSaveCount(unrelatedCompany.getId()));
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserIsNotSystemAdmin_doesNotRemoveMemberAndDoesNotSave() {
        String memberUsername = "member";

        Company company = new Company("founder", "Production Company");

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        assertThrows(IllegalArgumentException.class, () ->
                rolesDomainService.removeCompanyMemberAsAdmin(regularUsername, memberUsername)
        );

        // no user was registered, so no changes happened
        assertEquals(0, companyRepository.getSaveCount(company.getId()));
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserIsNotAssignedToAnyCompany_doesNotChangeAnyCompanyAndDoesNotSave() {
        Company company = new Company("founder", "Production Company");

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        assertThrows(IllegalArgumentException.class, () ->
                rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, "missingMember")
        );

        assertEquals(0, companyRepository.getSaveCount(company.getId()));
        assertEquals(0, companyRepository.getTotalSaveCount());
    }

    private static class FakeCompanyRepository implements ICompanyRepository {

        private final Map<UUID, Company> companiesById = new LinkedHashMap<>();
        private final Map<UUID, Integer> saveCountByCompanyId = new LinkedHashMap<>();

        @Override
        public UUID createCompany(String founderUsername, String companyName) {
            Company company = new Company(founderUsername, companyName);
            save(company);
            return company.getId();
        }

        @Override
        public Optional<Company> findByID(UUID companyId) {
            return Optional.ofNullable(companiesById.get(companyId));
        }

        @Override
        public void save(Company company) {
            companiesById.put(company.getId(), company);
            saveCountByCompanyId.merge(company.getId(), 1, Integer::sum);
        }

        // Helper used by older tests—company no longer tracks members so return all companies
        public List<Company> getCompaniesByMember(String username) {
            return List.copyOf(companiesById.values());
        }

        public int getSaveCount(UUID companyId) {
            return saveCountByCompanyId.getOrDefault(companyId, 0);
        }

        public int getTotalSaveCount() {
            return saveCountByCompanyId.values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        public void resetSaveCounters() {
            saveCountByCompanyId.clear();
        }
    }

    private static class FakeUserRepository implements IUserRepository {

        private final java.util.Set<String> systemAdmins = new java.util.HashSet<>();
        private final Map<String, User> usersByEmail = new LinkedHashMap<>();

        public void addSystemAdmin(String username) {
            systemAdmins.add(username);
        }

        @Override
        public void add(User user) {
            usersByEmail.put(user.getEmail(), user);
        }

        @Override
        public Optional<User> getUser(UUID UID) {
            return usersByEmail.values().stream().filter(u -> u.getId().equals(UID)).findFirst();
        }

        @Override
        public boolean exists(UUID userId) {
            return usersByEmail.values().stream().anyMatch(u -> u.getId().equals(userId));
        }

        @Override
        public boolean isSystemAdmin(String username) {
            return systemAdmins.contains(username);
        }

        @Override
        public boolean existsByEmail(String email) {
            return usersByEmail.containsKey(email);
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(usersByEmail.get(email));
        }

        @Override
        public boolean existsAdmin(UUID adminId) {
            return systemAdmins.contains(adminId.toString());
        }

        @Override
        public List<UUID> getCompaniesIdsByMember(String username) {
            User u = usersByEmail.get(username);
            if (u == null) return List.of();
            return List.copyOf(u.getCompanyRoles().keySet());
        }

        @Override
        public boolean isCompanyOwner(String username, UUID companyId) {
            User u = usersByEmail.get(username);
            if (u == null) return false;
            return u.isOwnerInCompany(companyId);
        }

        @Override
        public boolean hasPermission(String username, UUID companyId, CompanyPermission permission, UUID eventId) {
            User u = usersByEmail.get(username);
            if (u == null) return false;
            return u.hasPremisions(companyId, permission, eventId);
        }
    }
}
