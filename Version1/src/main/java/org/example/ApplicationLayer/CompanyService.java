package org.example.ApplicationLayer;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.DomainException;
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

    public void closeCompanyAsAdmin(String adminUsername, UUID companyId) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId);
    }

    public void inviteCompanyManager(String ownerUsername, UUID companyId, String usernameToInvite, Set<CompanyPermission> premissions) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        rolesDomainService.inviteCompanyManager(ownerUsername, companyId, usernameToInvite, premissions);
    }

    public void removeCompanyMemberAsOwner(String ownerUsername, UUID companyId, String usernameToRemove) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        rolesDomainService.removeCompanyMemberAsOwner(ownerUsername, companyId, usernameToRemove);
    }

    public void inviteCompanyOwner(String ownerUsername, UUID companyId, String usernameToInvite) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        rolesDomainService.inviteCompanyOwner(ownerUsername, companyId, usernameToInvite);
    }

    public void acceptCompanyInvitation(UUID invetationID, UUID companyId) {
         rolesDomainService.acceptCompanyInvitation(invetationID, companyId);
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

    public void removeCompanyMemberAsAdmin(String adminUsername, String usernameToRemove) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        if (usernameToRemove == null || usernameToRemove.isBlank()) {
            throw new IllegalArgumentException("Username to remove is required");
        }

        rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);
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

    public void rateCompany(UUID userID, UUID companyID, int rating)
    {
        if (rating < 0 || rating > 5)
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        try
        {
            rolesDomainService.rateCompany(userID, companyID, rating);
        }
        catch (DomainException e)
        {
            //TODO: Handle the domain exception appropriately
        }
    }

    public void changeManagerPermissions(String ownerUsername, UUID companyId, String managerUsername, Set<CompanyPermission> newPremissions) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        if (managerUsername == null || managerUsername.isBlank()) {
            throw new IllegalArgumentException("Manager username is required");
        }
        rolesDomainService.changeManagerPermissions(ownerUsername, companyId, managerUsername, newPremissions);
    }

    public String getCompanyHierarchyMermaid(UUID companyId, String requesterUsername) {
        if (requesterUsername == null || requesterUsername.isBlank()) {
            throw new IllegalArgumentException("Requester username is required");
        }
        return rolesDomainService.getCompanyHierarchyMermaid(companyId, requesterUsername);
    }
}
