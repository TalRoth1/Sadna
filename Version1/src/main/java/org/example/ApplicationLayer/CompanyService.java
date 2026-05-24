package org.example.ApplicationLayer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.ApplicationLayer.dto.CompanyDTOs.CompanyAccessResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.CompanyMembershipResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.CompanyResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.HierarchyResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.InvitationResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.SalesReportResponse;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.RolesDomainService;
import org.springframework.stereotype.Service;

@Service
public class CompanyService {
    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());

    private final RolesDomainService rolesDomainService;
    private final PurchaseDomainService purchaseDomainService;
    private final INotifier notifier;

    public CompanyService(RolesDomainService rolesDomainService,
            PurchaseDomainService purchaseDomainService,
            INotifier notifier) {
        this.rolesDomainService = rolesDomainService;
        this.purchaseDomainService = purchaseDomainService;
        this.notifier = notifier;
    }

    public CompanyResponse createCompany(String founderEmail, String companyName) {
        logger.info("caller=" + founderEmail
                + ", action=createCompany"
                + ", target=RolesDomainService.createCompany"
                + ", params={founderEmail=" + founderEmail + ", companyName=" + companyName + "}");

        if (founderEmail == null || founderEmail.isBlank()) {
            throw new IllegalArgumentException("Founder email is required");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name is required");
        }

        UUID companyId = rolesDomainService.createCompany(founderEmail, companyName);

        return new CompanyResponse(
                companyId,
                companyName,
                founderEmail,
                0.0,
                true,
                List.of());
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
            if (companyId == null) {
                throw new IllegalArgumentException("Company ID is required");
            }

            rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId);

            String owner = rolesDomainService.getCompanyOwner(companyId);

            notifier.notifyUser(owner, "Company: " + companyId + " has been closed");

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


    public InvitationResponse inviteCompanyManager(String ownerUsername,
            UUID companyId,
            String usernameToInvite,
            Set<CompanyPermission> premissions) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        UUID invitationId = rolesDomainService.inviteCompanyManager(
                ownerUsername,
                companyId,
                usernameToInvite,
                premissions);

        return new InvitationResponse(
                invitationId,
                companyId,
                ownerUsername,
                usernameToInvite,
                "MANAGER",
                premissions);
    }

    public InvitationResponse inviteCompanyOwner(String ownerUsername,
            UUID companyId,
            String usernameToInvite) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        UUID invitationId = rolesDomainService.inviteCompanyOwner(
                ownerUsername,
                companyId,
                usernameToInvite);

        return new InvitationResponse(
                invitationId,
                companyId,
                ownerUsername,
                usernameToInvite,
                "OWNER",
                null);
    }

    public void acceptCompanyInvitation(UUID invitationId, String username, UUID companyId) {
        if (invitationId == null) {
            throw new IllegalArgumentException("Invitation ID is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }

        rolesDomainService.acceptCompanyInvitation(invitationId, username, companyId);
    }

    public void removeCompanyMemberAsOwner(String ownerUsername, UUID companyId, String usernameToRemove) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }
        rolesDomainService.removeCompanyMemberAsOwner(ownerUsername, companyId, usernameToRemove);
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

    public void changeManagerPermissions(String ownerUsername,
            UUID companyId,
            String managerUsername,
            Set<CompanyPermission> newPermissions) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }
        if (managerUsername == null || managerUsername.isBlank()) {
            throw new IllegalArgumentException("Manager username is required");
        }

        rolesDomainService.changeManagerPermissions(
                ownerUsername, companyId, managerUsername, newPermissions);

        notifier.notifyUser(managerUsername, "Your permissions changed");
    }

    public void addPolicyRule(String username,
            UUID companyId,
            Float age,
            Integer minTicket,
            Integer maxTicket,
            Boolean allowLoneSeat) {
        addPolicyRule(
                username,
                companyId,
                Optional.ofNullable(age),
                Optional.ofNullable(minTicket),
                Optional.ofNullable(maxTicket),
                Optional.ofNullable(allowLoneSeat),
                true);
    }

    public void addPolicyRule(String username,
            UUID companyId,
            Optional<Float> age,
            Optional<Integer> minTicket,
            Optional<Integer> maxTicket,
            Optional<Boolean> allowLoneSeat,
            boolean andOr) {
        if (age != null && age.isPresent() && age.get() < 0) {
            throw new IllegalArgumentException("Age must be non negative");
        }
        if (minTicket != null && minTicket.isPresent() && minTicket.get() < 0) {
            throw new IllegalArgumentException("Minimum ticket amount must be non negative");
        }
        if (maxTicket != null && maxTicket.isPresent() && maxTicket.get() < 0) {
            throw new IllegalArgumentException("Maximum ticket amount must be non negative");
        }

        rolesDomainService.addPurchasePolicy(
                username, companyId, age, minTicket, maxTicket, allowLoneSeat, andOr);
    }

    public void deletePolicyRule(String username, UUID companyId, UUID ruleId) {
        if (ruleId == null) {
            throw new IllegalArgumentException("Rule ID is required");
        }

        rolesDomainService.deletePurchasePolicy(username, companyId, ruleId);
    }

    public void addOvertDiscount(String username,
            UUID companyId,
            LocalDate fromDate,
            LocalDate toDate,
            float discountPercent) {
        validateDiscount(toDate, discountPercent);
        rolesDomainService.addOvertDiscount(username, companyId, fromDate, toDate, discountPercent);
    }

    public void addConditionalDiscount(String username,
            UUID companyId,
            LocalDate fromDate,
            LocalDate toDate,
            float discountPercent,
            int requiredTickets,
            int appliedTickets) {
        validateDiscount(toDate, discountPercent);

        if (requiredTickets < 0) {
            throw new IllegalArgumentException("Required tickets must be non negative");
        }
        if (appliedTickets < 0) {
            throw new IllegalArgumentException("Applied tickets must be non negative");
        }

        rolesDomainService.addConditionalDiscount(
                username, companyId, fromDate, toDate,
                discountPercent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(String username,
            UUID companyId,
            LocalDate fromDate,
            LocalDate toDate,
            float discountPercent,
            String code) {
        validateDiscount(toDate, discountPercent);

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Coupon code is required");
        }

        rolesDomainService.addCouponCode(
                username, companyId, fromDate, toDate, discountPercent, code);
    }

    public void removeDiscount(String username, UUID companyId, UUID discountId) {
        if (discountId == null) {
            throw new IllegalArgumentException("Discount ID is required");
        }

        rolesDomainService.removeDiscount(username, companyId, discountId);
    }

    public void rateCompany(UUID userId, UUID companyId, int rating) {
        if (rating < 0 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        }

        try {
            rolesDomainService.rateCompany(userId, companyId, rating);
        } catch (DomainException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public HierarchyResponse getCompanyHierarchyMermaid(UUID companyId, String requesterEmail) {
        if (requesterEmail == null || requesterEmail.isBlank()) {
            throw new IllegalArgumentException("Requester email is required");
        }

        String mermaid = rolesDomainService.getCompanyHierarchyMermaid(companyId, requesterEmail);
        return new HierarchyResponse(companyId, mermaid);
    }

    public SalesReportResponse getSalesReportForOwner(String ownerUsername, UUID companyId) {
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new IllegalArgumentException("Owner username is required");
        }

        String report = purchaseDomainService.getSalesReportForOwner(ownerUsername, companyId).toString();
        return new SalesReportResponse(companyId, ownerUsername, report);
    }

    private void validateDiscount(LocalDate toDate, float discountPercent) {
        if (toDate == null) {
            throw new IllegalArgumentException("toDate is required");
        }
        if (toDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("toDate is before today");
        }
        if (discountPercent < 0.0f || discountPercent > 100.0f) {
            throw new IllegalArgumentException("Discount percent must be between 0 and 100");
        }
    }

    public List<CompanyMembershipResponse> getUserCompanies(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return rolesDomainService.getUserCompanies(userEmail);
    }

    public CompanyAccessResponse getCompanyAccess(UUID companyId, String userEmail) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        return rolesDomainService.getCompanyAccess(companyId, userEmail);
    }
}