package org.example.DomainLayer;

import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;

import java.time.LocalDate;
import java.util.List;

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

    public void removeCompanyMember(String adminUsername, UUID companyId, String usernameToRemove) {
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

    public void addAgePolicy(UUID companyId, float age)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.addAgePolicy(age);
    }

    public void deleteAgePolicy(UUID companyId)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.deleteAgePolicy();
    }

    public void addMinTicketPolicy(UUID companyId, int minTicket)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.addMinTicketPolicy(minTicket);
    }

    public void deleteMinTicketPolicy(UUID companyId)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.deleteMinTicketPolicy();
    }

    public void addMaxTicketPolicy(UUID companyId, int maxTicket)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.addMaxTicketPolicy(maxTicket);
    }
    public void deleteMaxTicketPolicy(UUID companyId)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.deleteMaxTicketPolicy();
    }

    public void addLoneSeatPolicy(UUID companyId, boolean allowLoneSeat)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.addLoneSeatPolicy(allowLoneSeat);
    }

    public void deleteLoneSeatPolicy(UUID companyId)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.deleteLoneSeatPolicy();
    }

    public void addOvertDiscount(UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.addOvertDiscount(fromDate, toDate, discountPrecent);
    }

    public void addConditionalDiscount(UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent, int requiredTickets, int appliedTickets)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.addConditionalDiscount(fromDate, toDate, discountPrecent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent, String code)
    {
        Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.addCouponCode(fromDate, toDate, discountPrecent, code);
    }

    public void removeDiscount(UUID companyId, UUID discountId)
    {
                Company company = companyRepository.findByID(companyId);
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.removeDiscount(discountId); 
    }
}
