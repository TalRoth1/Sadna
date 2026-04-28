package org.example.ApplicationLayer;

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

    public void removeCompanyMember(String adminUsername, int companyId, String usernameToRemove) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        rolesDomainService.removeCompanyMember(adminUsername, companyId, usernameToRemove);
    }
}
