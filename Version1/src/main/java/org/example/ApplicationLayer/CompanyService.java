package org.example.ApplicationLayer;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.RolesDomainService;

public class CompanyService {
    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());
    private final RolesDomainService rolesDomainService;
    private final PurchaseDomainService purchaseDomainService;

    public CompanyService(RolesDomainService rolesDomainService, PurchaseDomainService purchaseDomainService) {
        this.rolesDomainService = rolesDomainService;
        this.purchaseDomainService = purchaseDomainService;
    }
    public UUID createCompany(String founderUsername, String companyName) {
        logger.info("Attempting to create company: '" + companyName + "' for founder: " + founderUsername);
        
        try {
            if (founderUsername == null || founderUsername.isBlank()) {
                String errorMsg = "Company creation failed: founder username is required";
                logger.warning(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }

            UUID newCompanyId = rolesDomainService.createCompany(founderUsername, companyName);
            
            logger.info("Successfully created company '" + companyName + "' with ID: " + newCompanyId);
            return newCompanyId;

        } catch (Exception e) {
            logger.severe("Failed to create company '" + companyName + "'. Error: " + e.getMessage());
            throw e;
        }
    }

    public void closeCompanyAsAdmin(String adminUsername, UUID companyId) {
        logger.info("caller=" + adminUsername
                + ", action=closeCompanyAsAdmin"
                + ", target=RolesDomainService.closeCompanyAsAdmin"
                + ", params={adminUsername=" + adminUsername + ", companyId=" + companyId + "}");

        try {
            if (adminUsername == null || adminUsername.isBlank()) {
                throw new IllegalArgumentException("Admin username is required");
            }

            rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId);

            logger.info("action=closeCompanyAsAdmin completed successfully"
                    + ", params={adminUsername=" + adminUsername + ", companyId=" + companyId + "}");

        } catch (RuntimeException e) {
            logger.severe("action=closeCompanyAsAdmin failed"
                    + ", caller=" + adminUsername
                    + ", target=RolesDomainService.closeCompanyAsAdmin"
                    + ", params={adminUsername=" + adminUsername + ", companyId=" + companyId + "}"
                    + ", error=" + e.getMessage());
            throw e;
        }
    }

    public UUID inviteCompanyManager(String ownerUsername, UUID companyId, String usernameToInvite, Set<CompanyPermission> premissions) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        return rolesDomainService.inviteCompanyManager(ownerUsername, companyId, usernameToInvite, premissions);
    }

    public void removeCompanyMemberAsOwner(String ownerUsername, UUID companyId, String usernameToRemove) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        rolesDomainService.removeCompanyMemberAsOwner(ownerUsername, companyId, usernameToRemove);
    }

    public UUID inviteCompanyOwner(String ownerUsername, UUID companyId, String usernameToInvite) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        return rolesDomainService.inviteCompanyOwner(ownerUsername, companyId, usernameToInvite);
    }

    public void acceptCompanyInvitation(UUID invetationID, String username, UUID companyId) {
        rolesDomainService.acceptCompanyInvitation(invetationID, username, companyId);
    }
    
    public void addPolicyRule(String username, UUID companyId, Optional<Float> age, Optional<Integer> minTicket, Optional<Integer> maxTicket, Optional<Boolean> allowLoneSeat)
    {
        logger.info("User '" + username + "' attempting to add/update policy rules for Company ID: " + companyId);

        try {
            // Validation Checks
            if (age.isPresent() && age.get() < 0) {
                logger.warning("Policy addition failed: Invalid age provided (" + age.get() + ") by user: " + username);
                throw new IllegalArgumentException("Age must be a non negative number");
            }
            if (minTicket.isPresent() && minTicket.get() < 0) {
                logger.warning("Policy addition failed: Invalid minTicket (" + minTicket.get() + ") by user: " + username);
                throw new IllegalArgumentException("Minimum ticket amount must be a non negative integer");
            }
            if (maxTicket.isPresent() && maxTicket.get() < 0) {
                logger.warning("Policy addition failed: Invalid maxTicket (" + maxTicket.get() + ") by user: " + username);
                throw new IllegalArgumentException("maximum ticket amount must be a non negative integer");
            }

            rolesDomainService.addPurchasePolicy(username, companyId, age, minTicket, maxTicket, allowLoneSeat);
            
            logger.info("Successfully updated policy rules for Company ID: " + companyId + " by user: " + username);

        } catch (Exception e) {
            logger.severe("Unexpected error adding policy rule for Company ID: " + companyId + ". Error: " + e.getMessage());
            throw e;
        }
    }

    public void deletePolicyRule(String username, UUID companyId, boolean age, boolean minTicket, boolean maxTicket, boolean allowLoneSeat)
    {
        logger.info("User '" + username + "' attempting to delete specific policy rules for Company ID: " + companyId);

        try {
            rolesDomainService.deletePurchasePolicy(username, companyId, age, minTicket, maxTicket, allowLoneSeat);
            
            logger.info("Successfully deleted requested policy rules for Company ID: " + companyId + " by user: " + username);
            
        } catch (Exception e) {
            logger.severe("Failed to delete policy rules for Company ID: " + companyId + " by user: " + username + ". Error: " + e.getMessage());
            throw e;
        }
    }

    public void removeCompanyMemberAsAdmin(String adminUsername, String usernameToRemove) {
        logger.info("caller=" + adminUsername
                + ", action=removeCompanyMemberAsAdmin"
                + ", target=RolesDomainService.removeCompanyMemberAsAdmin"
                + ", params={adminUsername=" + adminUsername + ", usernameToRemove=" + usernameToRemove + "}");

        try {
            if (adminUsername == null || adminUsername.isBlank()) {
                throw new IllegalArgumentException("Admin username is required");
            }

            if (usernameToRemove == null || usernameToRemove.isBlank()) {
                throw new IllegalArgumentException("Username to remove is required");
            }

            rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

            logger.info("action=removeCompanyMemberAsAdmin completed successfully"
                    + ", params={adminUsername=" + adminUsername + ", usernameToRemove=" + usernameToRemove + "}");

        } catch (RuntimeException e) {
            logger.severe("action=removeCompanyMemberAsAdmin failed"
                    + ", caller=" + adminUsername
                    + ", target=RolesDomainService.removeCompanyMemberAsAdmin"
                    + ", params={adminUsername=" + adminUsername + ", usernameToRemove=" + usernameToRemove + "}"
                    + ", error=" + e.getMessage());
            throw e;
        }
    }
    public void addOvertDiscount(String username, UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent) {
        logger.info("User '" + username + "' attempting to add Overt Discount to Company ID: " + companyId + " (" + discountPrecent + "%)");

        try {
            if (toDate.isBefore(LocalDate.now())) {
                logger.warning("Overt Discount addition failed: toDate (" + toDate + ") is in the past. User: " + username);
                throw new IllegalArgumentException("toDate is before today");
            }
            if (discountPrecent > 100.0f || discountPrecent < 0.0f) {
                logger.warning("Overt Discount addition failed: Invalid percentage (" + discountPrecent + "). User: " + username);
                throw new IllegalArgumentException("Discount precent must be between 0 and 100");
            }

            rolesDomainService.addOvertDiscount(username, companyId, fromDate, toDate, discountPrecent);
            logger.info("Successfully added Overt Discount to Company ID: " + companyId + " by user: " + username);

        } catch (Exception e) {
            logger.severe("Error adding Overt Discount for Company ID: " + companyId + ". Error: " + e.getMessage());
            throw e;
        }
    }

    public void addConditionalDiscount(String username, UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent, int requiredTickets, int appliedTickets) {
        logger.info("User '" + username + "' attempting to add Conditional Discount to Company ID: " + companyId + 
                    " (Buy " + requiredTickets + " get " + appliedTickets + " at " + discountPrecent + "%)");

        try {
            if (toDate.isBefore(LocalDate.now())) {
                logger.warning("Conditional Discount addition failed: toDate is in the past. User: " + username);
                throw new IllegalArgumentException("toDate is before today");
            }
            if (discountPrecent > 100.0f || discountPrecent < 0.0f) {
                logger.warning("Conditional Discount addition failed: Invalid percentage (" + discountPrecent + "). User: " + username);
                throw new IllegalArgumentException("Discount precent must be between 0 and 100");
            }
            if (requiredTickets < 0) {
                logger.warning("Conditional Discount addition failed: Negative requiredTickets. User: " + username);
                throw new IllegalArgumentException("Required tickets must be non negative integers");
            }
            if (appliedTickets < 0) {
                logger.warning("Conditional Discount addition failed: Negative appliedTickets. User: " + username);
                throw new IllegalArgumentException("Applied tickets must be non negative integers");
            }

            rolesDomainService.addConditionalDiscount(username, companyId, fromDate, toDate, discountPrecent, requiredTickets, appliedTickets);
            logger.info("Successfully added Conditional Discount to Company ID: " + companyId + " by user: " + username);

        } catch (Exception e) {
            logger.severe("Error adding Conditional Discount for Company ID: " + companyId + ". Error: " + e.getMessage());
            throw e;
        }
    }

    public void addCouponCode(String username, UUID companyId, LocalDate fromDate, LocalDate toDate, float discountPrecent, String code) {
        logger.info("User '" + username + "' attempting to add Coupon Code '" + code + "' to Company ID: " + companyId);

        try {
            if (toDate.isBefore(LocalDate.now())) {
                logger.warning("Coupon addition failed: toDate is in the past. User: " + username);
                throw new IllegalArgumentException("toDate is before today");
            }
            if (discountPrecent > 100.0f || discountPrecent < 0.0f) {
                logger.warning("Coupon addition failed: Invalid percentage. User: " + username);
                throw new IllegalArgumentException("Discount precent must be between 0 and 100");
            }

            rolesDomainService.addCouponCode(username, companyId, fromDate, toDate, discountPrecent, code);
            logger.info("Successfully added Coupon Code '" + code + "' to Company ID: " + companyId + " by user: " + username);

        } catch (Exception e) {
            logger.severe("Error adding Coupon Code for Company ID: " + companyId + ". Error: " + e.getMessage());
            throw e;
        }
    }

    public void removeDiscount(String username, UUID companyId, UUID discountId) {
        logger.info("User '" + username + "' attempting to remove Discount ID: " + discountId + " from Company ID: " + companyId);

        try {
            rolesDomainService.removeDiscount(username, companyId, discountId);
            logger.info("Successfully removed Discount ID: " + discountId + " from Company ID: " + companyId + " by user: " + username);

        } catch (Exception e) {
            logger.severe("Failed to remove Discount ID: " + discountId + ". Error: " + e.getMessage());
            throw e;
        }
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
    
    public String getSalesReportForOwner(String ownerUsername, UUID companyId) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }
        return purchaseDomainService.getSalesReportForOwner(ownerUsername, companyId).toString();
    }
}
