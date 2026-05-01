package org.example.DomainLayer;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.Company;

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
    public void closeCompanyAsAdmin(String adminUsername, UUID companyId) {

        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        if (!userRepository.isSystemAdmin(adminUsername)) {
            throw new IllegalArgumentException("User is not system admin");
        }

        Company company = companyRepository.findByID(companyId).get();

        if (company == null) {
            throw new IllegalArgumentException("Company not found");
        }

        if (!company.isActive()) {
            throw new IllegalStateException("Company already inactive");
        }

        company.AdminClose();

        companyRepository.save(company);

        // TODO next step
        // notificationService.notifyCompanyClosed(...)
    }

    public void removeCompanyMemberAsAdmin(String adminUsername, int companyId, String usernameToRemove) {
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
            company.removeMemberAsAdmin(usernameToRemove);
            companyRepository.save(company);
        }

        // TODO: notify user after notification mechanism is implemented
    }

    public void addPurchasePolicy(UUID companyId, Optional<Float> age, Optional<Integer> minTicket, Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat)
    {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.addPurchasePolicy(age, minTicket, maxTicket, allowLoneSeat);
    }

    public void deletePurchasePolicy(UUID companyId, boolean age, boolean minTicket, boolean maxTicket, boolean allowLoneSeat)
    {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.deletePurchaseRule(age, minTicket, maxTicket, allowLoneSeat);
    }

    public void addOvertDiscount(UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent)
    {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.addOvertDiscount(fromDate, toDate, discountPrecent);
    }

    public void addConditionalDiscount(UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent, int requiredTickets, int appliedTickets)
    {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.addConditionalDiscount(fromDate, toDate, discountPrecent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent, String code)
    {
        Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.addCouponCode(fromDate, toDate, discountPrecent, code);
    }

    public void removeDiscount(UUID companyId, UUID discountId)
    {
                Company company = companyRepository.findByID(companyId).get();
        if (company == null)
            throw new IllegalArgumentException("Company not found");
        company.removeDiscount(discountId); 
    }

    public void rateCompany(UUID userID, UUID companyID, int rating)
    {
        Company company = companyRepository.findByID(companyID).get();

        if (company == null)
            throw new DomainException("Event not found while rating");

        if (userID == null)
            throw new DomainException("User not found while rating");

        company.addRating(userID, rating);
        companyRepository.save(company);
    }
}
