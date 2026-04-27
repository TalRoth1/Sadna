package org.example.ApplicationLayer;

import java.util.UUID;

import org.example.DomainLayer.RolesDomainService;

public class CompanyService {
    private final RolesDomainService rolesDomainService;

    public CompanyService(RolesDomainService rolesDomainService) {
        this.rolesDomainService = rolesDomainService;
    }

    public void closeCompany(String adminUsername, UUID companyId) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        rolesDomainService.closeCompany(adminUsername, companyId);
    }
}
