package org.example.ApplicationLayer;

import java.time.LocalDate;
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

    public void removeCompanyMember(String adminUsername, UUID companyId, String usernameToRemove) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        rolesDomainService.removeCompanyMember(adminUsername, companyId, usernameToRemove);
    }

    public void addOvertDiscount(UUID companyId ,LocalDate fromDate, LocalDate toDate, float discountPrecent)
    {
        if(toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if(discountPrecent > 100.0f || discountPrecent < 0.0f)
            throw new IllegalArgumentException("Discount precent must be between 0 and 100");
        rolesDomainService.addOvertDiscount(companyId, fromDate, toDate, discountPrecent);
    }

    public void addConditionalDiscount(UUID companyId ,LocalDate fromDate, LocalDate toDate, float discountPrecent, int requiredTickets, int appliedTickets)
    {
        if(toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if(discountPrecent > 100.0f || discountPrecent < 0.0f)
            throw new IllegalArgumentException("Discount precent must be between 0 and 100");
        if(requiredTickets < 0 )
            throw new IllegalArgumentException("Required tickets must be non negative integers");
        if(appliedTickets < 0 )
            throw new IllegalArgumentException("Applied tickets must be non negative integers");
        rolesDomainService.addConditionalDiscount(companyId, fromDate, toDate, discountPrecent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent, String code)
    {
        if(toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if(discountPrecent > 100.0f || discountPrecent < 0.0f)
            throw new IllegalArgumentException("Discount precent must be between 0 and 100");
        rolesDomainService.addCouponCode(companyId, fromDate, toDate, discountPrecent, code);
    }
    
    public void removeDiscount(UUID companyId, UUID discountId)
    {
        rolesDomainService.removeDiscount(companyId, discountId);
    }
}
