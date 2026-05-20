package org.example.ApplicationLayer;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.CompanyAggregate.Invitation;
import org.example.DomainLayer.CompanyAggregate.ManagerInvetation;
import org.example.DomainLayer.CompanyAggregate.OwnerInvetation;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.RolesDomainService;
import org.example.ApplicationLayer.dto.CompanyDTOs.CompanyResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.HierarchyResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.InvitationResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.SalesReportResponse;

/**
 * CompanyService
 *
 * Returns raw payload DTOs (CompanyResponse, InvitationResponse, etc.) — not wrapped.
 * The Controller wraps them in ApiResponse<T> before sending to the client.
 *
 * Operations with no return value return void — the Controller turns those
 * into ApiResponse.success(message) directly.
 *
 * The Service is the only layer that touches Domain objects.
 * All Domain → DTO mapping is centralized in the private mapper methods
 * at the bottom of this class.
 */
public class CompanyService {

    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());

    private final RolesDomainService rolesDomainService;
    private final PurchaseDomainService purchaseDomainService;

    public CompanyService(RolesDomainService rolesDomainService, PurchaseDomainService purchaseDomainService) {
        this.rolesDomainService = rolesDomainService;
        this.purchaseDomainService = purchaseDomainService;
    }

    // ================================================================
    //  PUBLIC API — business operations
    // ================================================================

    // ---------- Company creation & closing ----------

    public CompanyResponse createCompany(String founderUsername, String companyName) {
        if (founderUsername == null || founderUsername.isBlank())
            throw new IllegalArgumentException("Founder username is required");

        Company company = rolesDomainService.createCompany(founderUsername, companyName);
        return toCompanyResponse(company);
    }

    public void closeCompanyAsAdmin(String adminUsername, UUID companyId) {
        if (adminUsername == null || adminUsername.isBlank())
            throw new IllegalArgumentException("Admin username is required");
        rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId);
    }

    // ---------- Invitations ----------

    public InvitationResponse inviteCompanyManager(String ownerUsername, UUID companyId,
                                                   String usernameToInvite,
                                                   Set<CompanyPermission> permissions) {
        if (ownerUsername == null || ownerUsername.isBlank())
            throw new IllegalArgumentException("Owner username is required");

        Invitation invitation = rolesDomainService.inviteCompanyManager(
                ownerUsername, companyId, usernameToInvite, permissions);
        return toInvitationResponse(invitation);
    }

    public InvitationResponse inviteCompanyOwner(String ownerUsername, UUID companyId,
                                                 String usernameToInvite) {
        if (ownerUsername == null || ownerUsername.isBlank())
            throw new IllegalArgumentException("Owner username is required");

        Invitation invitation = rolesDomainService.inviteCompanyOwner(
                ownerUsername, companyId, usernameToInvite);
        return toInvitationResponse(invitation);
    }

    public void acceptCompanyInvitation(UUID invitationId, UUID companyId) {
        rolesDomainService.acceptCompanyInvitation(invitationId, companyId);
    }

    // ---------- Member management ----------

    public void removeCompanyMemberAsOwner(String ownerUsername, UUID companyId,
                                           String usernameToRemove) {
        if (ownerUsername == null || ownerUsername.isBlank())
            throw new IllegalArgumentException("Owner username is required");
        rolesDomainService.removeCompanyMemberAsOwner(ownerUsername, companyId, usernameToRemove);
    }

    public void removeCompanyMemberAsAdmin(String adminUsername, String usernameToRemove) {
        if (adminUsername == null || adminUsername.isBlank())
            throw new IllegalArgumentException("Admin username is required");
        if (usernameToRemove == null || usernameToRemove.isBlank())
            throw new IllegalArgumentException("Username to remove is required");
        rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);
    }

    public void changeManagerPermissions(String ownerUsername, UUID companyId,
                                         String managerUsername,
                                         Set<CompanyPermission> newPermissions) {
        if (ownerUsername == null || ownerUsername.isBlank())
            throw new IllegalArgumentException("Owner username is required");
        if (managerUsername == null || managerUsername.isBlank())
            throw new IllegalArgumentException("Manager username is required");
        rolesDomainService.changeManagerPermissions(ownerUsername, companyId, managerUsername, newPermissions);
    }

    // ---------- Purchase Policy ----------

    /**
     * Adds a purchase policy rule. Each parameter is nullable —
     * null means "this rule isn't being set right now."
     */
    public void addPolicyRule(String username, UUID companyId,
                              Float age, Integer minTicket,
                              Integer maxTicket, Boolean allowLoneSeat) {
        if (age != null && age < 0)
            throw new IllegalArgumentException("Age must be a non negative number");
        if (minTicket != null && minTicket < 0)
            throw new IllegalArgumentException("Minimum ticket amount must be a non negative integer");
        if (maxTicket != null && maxTicket < 0)
            throw new IllegalArgumentException("Maximum ticket amount must be a non negative integer");

        rolesDomainService.addPurchasePolicy(username, companyId, age, minTicket, maxTicket, allowLoneSeat);
    }

    public void deletePolicyRule(String username, UUID companyId,
                                 boolean age, boolean minTicket,
                                 boolean maxTicket, boolean allowLoneSeat) {
        rolesDomainService.deletePurchasePolicy(username, companyId, age, minTicket, maxTicket, allowLoneSeat);
    }

    // ---------- Discounts ----------

    public void addOvertDiscount(String username, UUID companyId,
                                 LocalDate fromDate, LocalDate toDate,
                                 float discountPercent) {
        if (toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if (discountPercent > 100.0f || discountPercent < 0.0f)
            throw new IllegalArgumentException("Discount percent must be between 0 and 100");
        rolesDomainService.addOvertDiscount(username, companyId, fromDate, toDate, discountPercent);
    }

    public void addConditionalDiscount(String username, UUID companyId,
                                       LocalDate fromDate, LocalDate toDate,
                                       float discountPercent, int requiredTickets,
                                       int appliedTickets) {
        if (toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if (discountPercent > 100.0f || discountPercent < 0.0f)
            throw new IllegalArgumentException("Discount percent must be between 0 and 100");
        if (requiredTickets < 0)
            throw new IllegalArgumentException("Required tickets must be non negative integers");
        if (appliedTickets < 0)
            throw new IllegalArgumentException("Applied tickets must be non negative integers");
        rolesDomainService.addConditionalDiscount(username, companyId, fromDate, toDate,
                discountPercent, requiredTickets, appliedTickets);
    }

    public void addCouponCode(String username, UUID companyId,
                              LocalDate fromDate, LocalDate toDate,
                              float discountPercent, String code) {
        if (toDate.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("toDate is before today");
        if (discountPercent > 100.0f || discountPercent < 0.0f)
            throw new IllegalArgumentException("Discount percent must be between 0 and 100");
        rolesDomainService.addCouponCode(username, companyId, fromDate, toDate, discountPercent, code);
    }

    public void removeDiscount(String username, UUID companyId, UUID discountId) {
        rolesDomainService.removeDiscount(username, companyId, discountId);
    }

    // ---------- Rating & Info ----------

    public void rateCompany(UUID userId, UUID companyId, int rating) {
        if (rating < 0 || rating > 5)
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        try {
            rolesDomainService.rateCompany(userId, companyId, rating);
        } catch (DomainException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public HierarchyResponse getCompanyHierarchyMermaid(UUID companyId, String requesterUsername) {
        if (requesterUsername == null || requesterUsername.isBlank())
            throw new IllegalArgumentException("Requester username is required");

        String mermaid = rolesDomainService.getCompanyHierarchyMermaid(companyId, requesterUsername);
        return toHierarchyResponse(companyId, mermaid);
    }

    public SalesReportResponse getSalesReportForOwner(String ownerUsername, UUID companyId) {
        if (ownerUsername == null || ownerUsername.isBlank())
            throw new IllegalArgumentException("Owner username is required");

        String report = purchaseDomainService.getSalesReportForOwner(ownerUsername, companyId).toString();
        return toSalesReportResponse(companyId, ownerUsername, report);
    }

    // ================================================================
    //  PRIVATE MAPPERS — Domain → DTO
    // ================================================================

    private CompanyResponse toCompanyResponse(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getFounder().getUsername(),
                company.getRating(),
                company.isActive(),
                company.getEventIds()
        );
    }

    private InvitationResponse toInvitationResponse(Invitation invitation) {
        String type;
        Set<CompanyPermission> permissions = null;

        if (invitation instanceof ManagerInvetation managerInvitation) {
            type = "MANAGER";
            permissions = managerInvitation.getPremissions();
        } else if (invitation instanceof OwnerInvetation) {
            type = "OWNER";
        } else {
            type = "UNKNOWN";
        }

        return new InvitationResponse(
                invitation.getId(),
                invitation.getCompanyId(),
                invitation.getAppointerUsername(),
                invitation.getAppointeeUsername(),
                type,
                permissions
        );
    }

    private HierarchyResponse toHierarchyResponse(UUID companyId, String mermaidChart) {
        return new HierarchyResponse(companyId, mermaidChart);
    }

    private SalesReportResponse toSalesReportResponse(UUID companyId, String ownerUsername, String report) {
        return new SalesReportResponse(companyId, ownerUsername, report);
    }
}