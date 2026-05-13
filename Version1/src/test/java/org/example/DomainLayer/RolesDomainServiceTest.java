package org.example.DomainLayer;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.UserAggregate.User;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
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
     * 6.1 - Close production company by System Admin
     */

    @Test
    public void closeCompanyAsAdmin_whenValidAdminClosesCompany_fullFlowUpdatesStateAndSaves() {
        Company company = new Company("founder", "Production Company");
        UUID companyId = company.getId();

        company.appointNewOwner("owner", "founder");
        company.appointNewManager("manager", "owner", Set.of(CompanyPermission.VIEW_HISTORY));

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId);

        Company result = companyRepository.findByID(companyId).orElseThrow();

        assertFalse(result.isActive());

        assertFalse(result.hasMember("founder"));
        assertFalse(result.hasMember("owner"));
        assertFalse(result.hasMember("manager"));

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
        assertFalse(result.hasMember("founder"));

        assertEquals(1, companyRepository.getSaveCount(companyId));
    }

    @Test
    public void closeCompanyAsAdmin_whenCompanyHasMultipleOwnersAndManagers_removesAllCompanyMembers() {
        Company company = new Company("founder", "Production Company");
        UUID companyId = company.getId();

        company.appointNewOwner("ownerA", "founder");
        company.appointNewOwner("ownerB", "founder");

        company.appointNewManager("managerA", "ownerA", Set.of(CompanyPermission.VIEW_HISTORY));
        company.appointNewManager("managerB", "ownerB", Set.of(CompanyPermission.MANAGE_POLICIES));

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId);

        Company result = companyRepository.findByID(companyId).orElseThrow();

        assertFalse(result.isActive());

        assertFalse(result.hasMember("founder"));
        assertFalse(result.hasMember("ownerA"));
        assertFalse(result.hasMember("ownerB"));
        assertFalse(result.hasMember("managerA"));
        assertFalse(result.hasMember("managerB"));

        assertEquals(1, companyRepository.getSaveCount(companyId));
    }

    @Test
    public void closeCompanyAsAdmin_whenUserIsNotSystemAdmin_doesNotChangeCompanyStateAndDoesNotSave() {
        Company company = new Company("founder", "Production Company");
        UUID companyId = company.getId();

        company.appointNewOwner("owner", "founder");
        company.appointNewManager("manager", "owner", Set.of(CompanyPermission.VIEW_HISTORY));

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        assertThrows(IllegalArgumentException.class, () ->
                rolesDomainService.closeCompanyAsAdmin(regularUsername, companyId)
        );

        Company result = companyRepository.findByID(companyId).orElseThrow();

        assertTrue(result.isActive());

        assertTrue(result.hasMember("founder"));
        assertTrue(result.hasMember("owner"));
        assertTrue(result.hasMember("manager"));

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

        company.appointNewOwner("owner", "founder");
        company.AdminClose();

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        assertThrows(IllegalStateException.class, () ->
                rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId)
        );

        Company result = companyRepository.findByID(companyId).orElseThrow();

        assertFalse(result.isActive());
        assertFalse(result.hasMember("founder"));
        assertFalse(result.hasMember("owner"));

        assertEquals(0, companyRepository.getSaveCount(companyId));
    }

    /*
     * 6.2 - Remove user subscription from all companies by System Admin
     */

    @Test
    public void removeCompanyMemberAsAdmin_whenUserExistsInMultipleCompanies_fullFlowRemovesFromAllRelevantCompaniesOnly() {
        String memberUsername = "member";

        Company firstCompany = new Company("founder1", "First Company");
        Company secondCompany = new Company("founder2", "Second Company");
        Company unrelatedCompany = new Company("founder3", "Unrelated Company");

        firstCompany.appointNewOwner(memberUsername, "founder1");
        secondCompany.appointNewManager(memberUsername, "founder2", Set.of(CompanyPermission.VIEW_HISTORY));

        companyRepository.save(firstCompany);
        companyRepository.save(secondCompany);
        companyRepository.save(unrelatedCompany);
        companyRepository.resetSaveCounters();

        rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, memberUsername);

        assertFalse(firstCompany.hasMember(memberUsername));
        assertFalse(secondCompany.hasMember(memberUsername));
        assertFalse(unrelatedCompany.hasMember(memberUsername));

        assertEquals(1, companyRepository.getSaveCount(firstCompany.getId()));
        assertEquals(1, companyRepository.getSaveCount(secondCompany.getId()));
        assertEquals(0, companyRepository.getSaveCount(unrelatedCompany.getId()));
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserExistsInOneCompany_removesOnlyFromThatCompanyAndSavesOnlyThatCompany() {
        String memberUsername = "member";

        Company companyWithMember = new Company("founder1", "Company With Member");
        Company companyWithoutMember = new Company("founder2", "Company Without Member");

        companyWithMember.appointNewOwner(memberUsername, "founder1");

        companyRepository.save(companyWithMember);
        companyRepository.save(companyWithoutMember);
        companyRepository.resetSaveCounters();

        rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, memberUsername);

        assertFalse(companyWithMember.hasMember(memberUsername));
        assertFalse(companyWithoutMember.hasMember(memberUsername));

        assertEquals(1, companyRepository.getSaveCount(companyWithMember.getId()));
        assertEquals(0, companyRepository.getSaveCount(companyWithoutMember.getId()));
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserIsManager_removesManagerAndSavesCompany() {
        String managerUsername = "manager";

        Company company = new Company("founder", "Production Company");
        company.appointNewManager(managerUsername, "founder", Set.of(CompanyPermission.VIEW_HISTORY));

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, managerUsername);

        assertFalse(company.hasMember(managerUsername));
        assertTrue(company.hasMember("founder"));

        assertEquals(1, companyRepository.getSaveCount(company.getId()));
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserHasDifferentRolesInDifferentCompanies_removesFromAllRelevantCompanies() {
        String usernameToRemove = "targetUser";

        Company ownerCompany = new Company("founder1", "Owner Company");
        Company managerCompany = new Company("founder2", "Manager Company");
        Company unrelatedCompany = new Company("founder3", "Unrelated Company");

        ownerCompany.appointNewOwner(usernameToRemove, "founder1");
        managerCompany.appointNewManager(usernameToRemove, "founder2", Set.of(CompanyPermission.VIEW_HISTORY));

        companyRepository.save(ownerCompany);
        companyRepository.save(managerCompany);
        companyRepository.save(unrelatedCompany);
        companyRepository.resetSaveCounters();

        rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

        assertFalse(ownerCompany.hasMember(usernameToRemove));
        assertFalse(managerCompany.hasMember(usernameToRemove));
        assertFalse(unrelatedCompany.hasMember(usernameToRemove));

        assertEquals(1, companyRepository.getSaveCount(ownerCompany.getId()));
        assertEquals(1, companyRepository.getSaveCount(managerCompany.getId()));
        assertEquals(0, companyRepository.getSaveCount(unrelatedCompany.getId()));
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserIsNotSystemAdmin_doesNotRemoveMemberAndDoesNotSave() {
        String memberUsername = "member";

        Company company = new Company("founder", "Production Company");
        company.appointNewOwner(memberUsername, "founder");

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        assertThrows(IllegalArgumentException.class, () ->
                rolesDomainService.removeCompanyMemberAsAdmin(regularUsername, memberUsername)
        );

        assertTrue(company.hasMember(memberUsername));
        assertTrue(company.hasMember("founder"));

        assertEquals(0, companyRepository.getSaveCount(company.getId()));
    }

    @Test
    public void removeCompanyMemberAsAdmin_whenUserIsNotAssignedToAnyCompany_doesNotChangeAnyCompanyAndDoesNotSave() {
        Company company = new Company("founder", "Production Company");
        company.appointNewOwner("existingOwner", "founder");

        companyRepository.save(company);
        companyRepository.resetSaveCounters();

        assertThrows(IllegalArgumentException.class, () ->
                rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, "missingMember")
        );

        assertTrue(company.hasMember("founder"));
        assertTrue(company.hasMember("existingOwner"));

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
        public boolean isOwner(String username, UUID companyId) {
            Company company = companiesById.get(companyId);
            return company != null && company.isOwner(username);
        }

        @Override
        public void save(Company company) {
            companiesById.put(company.getId(), company);
            saveCountByCompanyId.merge(company.getId(), 1, Integer::sum);
        }

        @Override
        public List<Company> getCompaniesByMember(String username) {
            return companiesById.values().stream()
                    .filter(company -> company.hasMember(username))
                    .toList();
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

        private final Set<String> systemAdmins = new HashSet<>();

        public void addSystemAdmin(String username) {
            systemAdmins.add(username);
        }

        @Override
        public void add(User user) {
        }

        @Override
        public Optional<User> getUser(UUID UID) {
            return Optional.empty();
        }

        @Override
        public boolean exists(UUID userId) {
            return false;
        }

        @Override
        public boolean isSystemAdmin(String username) {
            return systemAdmins.contains(username);
        }

        @Override
        public boolean existsByEmail(String email) {
            return false;
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public boolean existsAdmin(UUID adminId) {
            return false;
        }
    }
}