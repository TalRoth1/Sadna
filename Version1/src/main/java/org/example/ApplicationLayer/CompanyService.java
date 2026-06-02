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
import org.example.ApplicationLayer.dto.CompanyDTOs.CompanyPoliciesResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.SalesReportResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.DiscountPolicyDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.DiscountRuleDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.PurchasePolicyDto;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.PurchaseRuleDto;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.RolesDomainService;
import org.example.ApplicationLayer.EventService;
import org.example.ApplicationLayer.dto.CompanyDTOs.EventDtos.EventSummaryDto;
import org.example.DomainLayer.PolicyManagment.AgeRule;
import org.example.DomainLayer.PolicyManagment.ConditionalDiscount;
import org.example.DomainLayer.PolicyManagment.CouponCode;
import org.example.DomainLayer.PolicyManagment.DiscountPolicy;
import org.example.DomainLayer.PolicyManagment.IDiscountRule;
import org.example.DomainLayer.PolicyManagment.IPurchaseRule;
import org.example.DomainLayer.PolicyManagment.LoneSeatRule;
import org.example.DomainLayer.PolicyManagment.MaxTicketRule;
import org.example.DomainLayer.PolicyManagment.MinTicketRule;
import org.example.DomainLayer.PolicyManagment.OvertDiscount;
import org.example.DomainLayer.PolicyManagment.PurchaseComposite;
import org.example.DomainLayer.PolicyManagment.PurchasePolicy;
import org.springframework.stereotype.Service;

@Service
public class CompanyService {
    private static final Logger logger = Logger.getLogger(CompanyService.class.getName());

    private final RolesDomainService rolesDomainService;
    private final PurchaseDomainService purchaseDomainService;
    private final INotifier notifier;
    private final EventService eventService;

    public CompanyService(RolesDomainService rolesDomainService,
            PurchaseDomainService purchaseDomainService,
            INotifier notifier,
            EventService eventService) {
        this.rolesDomainService = rolesDomainService;
        this.purchaseDomainService = purchaseDomainService;
        this.notifier = notifier;
        this.eventService = eventService;
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

        Company company = rolesDomainService.getCompany(companyId);

        return new InvitationResponse(
                invitationId,
                companyId,
            company.getName(),
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

        Company company = rolesDomainService.getCompany(companyId);

        return new InvitationResponse(
                invitationId,
                companyId,
            company.getName(),
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

    public void rejectCompanyInvitation(UUID invitationId, String username, UUID companyId) {
        if (invitationId == null) {
            throw new IllegalArgumentException("Invitation ID is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }

        rolesDomainService.rejectCompanyInvitation(invitationId, username, companyId);
    }

    public List<InvitationResponse> getUserInvitations(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("User email is required");
        }

        return rolesDomainService.getUserInvitations(userEmail);
    }

    public void removeCompanyMemberAsOwner(String ownerEmail, UUID companyId, String emailToRemove) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("Owner email is required");
        }
        if (emailToRemove == null || emailToRemove.isBlank()) {
            throw new IllegalArgumentException("Email to remove is required");
        }
        rolesDomainService.removeCompanyMemberAsOwner(ownerEmail, companyId, emailToRemove);
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

        notifier.notifyUser(managerUsername, "Your manager permissions have changed.");
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

    public SalesReportResponse getSalesReportForOwner(String ownerEmail, UUID companyId) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("Owner email is required");
        }

        org.example.ApplicationLayer.dto.CompanyDTOs.CompanyAccessResponse access =
            rolesDomainService.getCompanyAccess(companyId, ownerEmail);
        String normalizedRole = access.role == null ? "" : access.role.trim().toLowerCase();
        if (!normalizedRole.equals("owner") && !normalizedRole.equals("founder")) {
            throw new IllegalArgumentException("Only company owners can view the sales report");
        }

        org.example.ApplicationLayer.dto.SalesReport report =
            purchaseDomainService.getSalesReportForOwner(ownerEmail, companyId);
        return new SalesReportResponse(
                companyId,
                ownerEmail,
                report.getEventIds(),
                report.getTicketIds(),
                report.getTotalRevenue());
    }

    public List<org.example.ApplicationLayer.dto.CompanyDTOs.SubordinateEventDto> getEventsManagedBySubordinates(String ownerEmail, UUID companyId) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            throw new IllegalArgumentException("Owner email is required");
        }

        org.example.ApplicationLayer.dto.CompanyDTOs.CompanyAccessResponse access =
            rolesDomainService.getCompanyAccess(companyId, ownerEmail);
        String normalizedRole = access.role == null ? "" : access.role.trim().toLowerCase();
        if (!normalizedRole.equals("owner") && !normalizedRole.equals("founder")) {
            throw new IllegalArgumentException("Only company owners can view subordinate events");
        }

        List<String> managers = rolesDomainService.getOwnerAndSubordinatesUsernames(companyId, ownerEmail);

        List<org.example.ApplicationLayer.dto.CompanyDTOs.SubordinateEventDto> out = new java.util.ArrayList<>();
        java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();
        for (String mgr : managers) {
            List<EventSummaryDto> events = eventService.getEventsForUserInCompany(mgr, companyId);
            for (EventSummaryDto e : events) {
                if (!seen.contains(e.eventId())) {
                    seen.add(e.eventId());
                    out.add(new org.example.ApplicationLayer.dto.CompanyDTOs.SubordinateEventDto(
                            e.eventId(), e.companyId(), e.companyName(), e.companyRating(),
                            e.name(), e.artist(), e.eventType(), e.date(), e.location(), e.rating(),
                            e.priceMin(), e.priceMax(), e.availableTickets(), e.totalTickets(),
                            mgr
                    ));
                }
            }
        }
        return out;
    }

    public CompanyPoliciesResponse getCompanyPolicies(UUID companyId, String userEmail) {
        if (companyId == null) {
            throw new IllegalArgumentException("Company ID is required");
        }
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        rolesDomainService.getCompanyAccess(companyId, userEmail);
        Company company = rolesDomainService.getCompany(companyId);

        return new CompanyPoliciesResponse(
                company.getId(),
                company.getName(),
                toPurchasePolicyDto(company.getPurchasePolicy()),
                toDiscountPolicyDto(company.getDiscountPolicy()));
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
        logger.info("caller=" + userEmail
                + ", action=getUserCompanies"
                + ", target=RolesDomainService.getUserCompanies"
                + ", params={userEmail=" + userEmail + "}");
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

    private static PurchasePolicyDto toPurchasePolicyDto(PurchasePolicy purchasePolicy) {
        List<PurchaseRuleDto> out = new java.util.ArrayList<>();
        collectPurchaseLeaves(purchasePolicy == null ? null : purchasePolicy.getRulesView(), out);
        return new PurchasePolicyDto(out);
    }

    private static void collectPurchaseLeaves(IPurchaseRule rule, List<PurchaseRuleDto> out) {
        if (rule == null) {
            return;
        }

        if (rule instanceof PurchaseComposite composite) {
            collectPurchaseLeaves(composite.getLeftRule(), out);
            collectPurchaseLeaves(composite.getRightRule(), out);
            return;
        }

        if (rule instanceof AgeRule age) {
            out.add(new PurchaseRuleDto(age.getId(), "AGE", age.getMinAge(), null, null, null));
        } else if (rule instanceof MinTicketRule min) {
            out.add(new PurchaseRuleDto(min.getId(), "MIN_TICKETS", null, min.getMinTicket(), null, null));
        } else if (rule instanceof MaxTicketRule max) {
            out.add(new PurchaseRuleDto(max.getId(), "MAX_TICKETS", null, null, max.getMaxTicket(), null));
        } else if (rule instanceof LoneSeatRule lone) {
            out.add(new PurchaseRuleDto(lone.getId(), "LONE_SEAT", null, null, null, lone.isAllowLoneSeat()));
        }
    }

    private static DiscountPolicyDto toDiscountPolicyDto(DiscountPolicy discountPolicy) {
        List<DiscountRuleDto> out = new java.util.ArrayList<>();
        if (discountPolicy != null) {
            for (IDiscountRule rule : discountPolicy.getDiscountRules()) {
                if (rule instanceof OvertDiscount overt) {
                    out.add(new DiscountRuleDto(
                            overt.getId(),
                            "OVERT",
                            overt.getFromDate(),
                            overt.getToDate(),
                            overt.getDiscountPercent(),
                            null,
                            null,
                            null));
                } else if (rule instanceof ConditionalDiscount conditional) {
                    out.add(new DiscountRuleDto(
                            conditional.getId(),
                            "CONDITIONAL",
                            conditional.getFromDate(),
                            conditional.getToDate(),
                            conditional.getDiscountPercent(),
                            conditional.getRequiredTickets(),
                            conditional.getAppliedTickets(),
                            null));
                } else if (rule instanceof CouponCode coupon) {
                    out.add(new DiscountRuleDto(
                            coupon.getId(),
                            "COUPON",
                            coupon.getFromDate(),
                            coupon.getToDate(),
                            coupon.getDiscountPercent(),
                            null,
                            null,
                            coupon.getCode()));
                }
            }
        }

        return new DiscountPolicyDto(out);
    }
}