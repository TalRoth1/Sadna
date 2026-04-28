package org.example.ApplicationLayer;

import java.util.Optional;
import java.util.UUID;

import org.example.DomainLayer.RolesDomainService;

public class CompanyService {
    private final RolesDomainService rolesDomainService;

    public CompanyService(RolesDomainService rolesDomainService) {
        this.rolesDomainService = rolesDomainService;
    }
    public void createCompany(String founderUsername, String companyName)
    {
        if (founderUsername == null || founderUsername.isBlank()) 
            throw new IllegalArgumentException("founder username is required");
        rolesDomainService.createCompany(founderUsername, companyName);
    }
    public void closeCompany(String adminUsername, UUID companyId) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        rolesDomainService.closeCompany(adminUsername, companyId);
    }

    public void addPolicyRule(UUID companyId, Optional<Float> age, Optional<Integer> minTicket, Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat)
    {
        if (age.isPresent() && age.get() < 0)
            throw new IllegalArgumentException("Age must be a non negative number");
        if (minTicket.isPresent() && minTicket.get() < 0)
            throw new IllegalArgumentException("Minimum ticket amount must be a non negative integer");
        if (maxTicket.isPresent() && maxTicket.get() < 0)
            throw new IllegalArgumentException("maximum ticket amount must be a non negative integer");
        rolesDomainService.addPurchasePolicy(companyId, age, minTicket, maxTicket, allowLoneSeat);
    }
    
    public void deletePolicyRule(UUID companyId, boolean age, boolean minTicket, boolean maxTicket, boolean allowLoneSeat)
    {
        rolesDomainService.deletePurchasePolicy(companyId, age, minTicket, maxTicket, allowLoneSeat);
    }

    public void removeCompanyMember(String adminUsername, int companyId, String usernameToRemove) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        rolesDomainService.removeCompanyMember(adminUsername, companyId, usernameToRemove);
    }
}
