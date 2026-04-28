package org.example.DomainLayer;

import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;

import java.util.List;
import java.util.Optional;

public class RolesDomainService {

    private final ICompanyRepository companyRepository;
    private final IUserRepository userRepository;

    public RolesDomainService(ICompanyRepository companyRepository, IUserRepository userRepository) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    public void createCompany(String founderUsername, String companyName)
    {
        companyRepository.createCompany(founderUsername, companyName);
    }
    public void closeCompany(String adminUsername, UUID companyId) {

        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        if (!userRepository.isSystemAdmin(adminUsername)) {
            throw new IllegalArgumentException("User is not system admin");
        }

        Company company = companyRepository.findByID(companyId);

        if (company == null) {
            throw new IllegalArgumentException("Company not found");
        }

        if (!company.isActive()) {
            throw new IllegalStateException("Company already inactive");
        }

        company.close();

        companyRepository.save(company);

        // TODO next step
        // notificationService.notifyCompanyClosed(...)
    }

    public void removeCompanyMember(String adminUsername, int companyId, String usernameToRemove) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        if (usernameToRemove == null || usernameToRemove.isBlank()) {
            throw new IllegalArgumentException("Username to remove is required");
        }

        if (!userRepository.isSystemAdmin(adminUsername)) {
            throw new IllegalArgumentException("User is not system admin");
        }

        List<Company> companies = companyRepository.getCompaniesByMember(usernameToRemove);

        if (companies.isEmpty()) {
            throw new IllegalArgumentException("User is not assigned to any company");
        }

        for (Company company : companies) {
            company.removeMember(usernameToRemove);
            companyRepository.save(company);
        }

        // TODO: notify user after notification mechanism is implemented
    }

    public void addPurchasePolicy(UUID companyId, Optional<Float> age, Optional<Integer> minTicket, Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.addPurchasePolicy(age, minTicket, maxTicket, allowLoneSeat);
    }

    public void deletePurchasePolicy(UUID companyId, boolean age, boolean minTicket, boolean maxTicket, boolean allowLoneSeat)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.deletePurchaseRule(age, minTicket, maxTicket, allowLoneSeat);
    }
}
