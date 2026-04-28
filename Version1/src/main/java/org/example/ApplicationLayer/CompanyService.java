package org.example.ApplicationLayer;

<<<<<<< HEAD
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

    public void removeCompanyMember(String adminUsername, int companyId, String usernameToRemove) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        rolesDomainService.removeCompanyMember(adminUsername, companyId, usernameToRemove);
=======
import org.example.DomainLayer.ICompanyRepository;

public class CompanyService {
    private ICompanyRepository repo;
    
    public void createCompany(String founderUsername, String CompanyName)
    {
        repo.addCompany(founderUsername, CompanyName);
>>>>>>> 25e1b03cdd3fcd72bbd4ef158cdc419187a7fa9b
    }
}
