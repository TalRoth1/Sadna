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

    public void addAgePolicy(UUID companyId,float age)
    {
        if (age < 0)
            throw new IllegalArgumentException("Age must be a non negative number");
        rolesDomainService.addAgePolicy(companyId, age);
    }

    public void deleteAgePolicy(UUID companyId)
    {
        rolesDomainService.deleteAgePolicy(companyId);
    }

    public void addMinTicketPolicy(UUID companyId,int minTicket)
    {
        if (minTicket < 0)
            throw new IllegalArgumentException("Minimum ticket amount must be a non negative integer");
        rolesDomainService.addMinTicketPolicy(companyId, minTicket);
    }

    public void deleteMinTicketPolicy(UUID companyId)
    {
        rolesDomainService.deleteMinTicketPolicy(companyId);
    }

    public void addMaxTicketPolicy(UUID companyId,int maxTicket)
    {
        if (maxTicket < 0)
            throw new IllegalArgumentException("maximum ticket amount must be a non negative integer");
        rolesDomainService.addMaxTicketPolicy(companyId, maxTicket);
    }

    public void deleteMaxTicketPolicy(UUID companyId)
    {
        rolesDomainService.deleteMaxTicketPolicy(companyId);
    }

    public void addLoneSeatPolicy(UUID companyId, boolean allowLoneSeat)
    {
        rolesDomainService.addLoneSeatPolicy(companyId, allowLoneSeat);
    }

    public void deleteLoneSeatPolicy(UUID companyId)
    {
        rolesDomainService.deleteLoneSeatPolicy(companyId);
    }

    public void removeCompanyMember(String adminUsername, int companyId, String usernameToRemove) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        rolesDomainService.removeCompanyMember(adminUsername, companyId, usernameToRemove);
    }
}
