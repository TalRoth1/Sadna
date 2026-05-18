package org.example.API;

import org.example.ApplicationLayer.CompanyService;
import org.example.ApplicationLayer.dto.*;
import org.example.DomainLayer.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    public ResponseEntity<CompanyResponse> createCompany(@RequestBody CreateCompanyRequest request) {
        try {
            UUID companyId = companyService.createCompany(request.founderUsername, request.companyName);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new CompanyResponse(true, "Company created successfully", companyId));
        } catch (IllegalArgumentException | DomainException e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    // TODO (V3): Extract adminUsername from security context token
    @DeleteMapping("/{companyId}/admin-close")
    public ResponseEntity<CompanyResponse> closeCompanyAsAdmin(@PathVariable UUID companyId, @RequestBody AdminActionRequest request) {
        try {
            companyService.closeCompanyAsAdmin(request.adminUsername, companyId);
            return ResponseEntity.ok(new CompanyResponse(true, "Company closed by admin", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    // TODO (V3): Extract ownerUsername from security context token
    @PostMapping("/{companyId}/invite-manager")
    public ResponseEntity<CompanyResponse> inviteCompanyManager(@PathVariable UUID companyId, @RequestBody InviteMemberRequest request) {
        try {
            UUID inviteId = companyService.inviteCompanyManager(request.ownerUsername, companyId, request.usernameToInvite, request.permissions);
            return ResponseEntity.ok(new CompanyResponse(true, "Manager invited successfully", inviteId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    // TODO (V3): Extract ownerUsername from security context token
    @PostMapping("/{companyId}/invite-owner")
    public ResponseEntity<CompanyResponse> inviteCompanyOwner(@PathVariable UUID companyId, @RequestBody InviteMemberRequest request) {
        try {
            UUID inviteId = companyService.inviteCompanyOwner(request.ownerUsername, companyId, request.usernameToInvite);
            return ResponseEntity.ok(new CompanyResponse(true, "Owner invited successfully", inviteId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    /**
     * דיוק 1: שימוש ב-PathVariable במקום DTO ייעודי
     */
    @PostMapping("/{companyId}/invitations/{invitationId}/accept")
    public ResponseEntity<CompanyResponse> acceptCompanyInvitation(@PathVariable UUID companyId, @PathVariable UUID invitationId) {
        try {
            companyService.acceptCompanyInvitation(invitationId, companyId);
            return ResponseEntity.ok(new CompanyResponse(true, "Invitation accepted", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    // TODO (V3): Extract ownerUsername from security context token
    @DeleteMapping("/{companyId}/members/owner-remove")
    public ResponseEntity<CompanyResponse> removeCompanyMemberAsOwner(@PathVariable UUID companyId, @RequestBody OwnerActionRequest request) {
        try {
            companyService.removeCompanyMemberAsOwner(request.ownerUsername, companyId, request.targetUsername);
            return ResponseEntity.ok(new CompanyResponse(true, "Member removed by owner", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    // TODO (V3): Extract adminUsername from security context token
    @DeleteMapping("/admin-remove-member")
    public ResponseEntity<CompanyResponse> removeCompanyMemberAsAdmin(@RequestBody AdminActionRequest request) {
        try {
            companyService.removeCompanyMemberAsAdmin(request.adminUsername, request.targetUsername);
            return ResponseEntity.ok(new CompanyResponse(true, "Member removed globally by admin", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    @PutMapping("/{companyId}/manager-permissions")
    public ResponseEntity<CompanyResponse> changeManagerPermissions(@PathVariable UUID companyId, @RequestBody ChangePermissionsRequest request) {
        try {
            companyService.changeManagerPermissions(request.ownerUsername, companyId, request.managerUsername, request.newPermissions);
            return ResponseEntity.ok(new CompanyResponse(true, "Permissions updated", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    @PostMapping("/{companyId}/policies")
    public ResponseEntity<CompanyResponse> addPolicyRule(@PathVariable UUID companyId, @RequestBody PolicyRuleRequest request) {
        try {
            companyService.addPolicyRule(
                    request.username, companyId,
                    Optional.ofNullable(request.age),
                    Optional.ofNullable(request.minTicket),
                    Optional.ofNullable(request.maxTicket),
                    Optional.ofNullable(request.allowLoneSeat)
            );
            return ResponseEntity.ok(new CompanyResponse(true, "Policy rule added", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    @DeleteMapping("/{companyId}/policies")
    public ResponseEntity<CompanyResponse> deletePolicyRule(@PathVariable UUID companyId, @RequestBody PolicyRuleRequest request) {
        try {
            companyService.deletePolicyRule(
                    request.username, companyId,
                    request.age != null,
                    request.minTicket != null,
                    request.maxTicket != null,
                    request.allowLoneSeat != null
            );
            return ResponseEntity.ok(new CompanyResponse(true, "Policy rule deleted", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    @PostMapping("/{companyId}/discounts/overt")
    public ResponseEntity<CompanyResponse> addOvertDiscount(@PathVariable UUID companyId, @RequestBody DiscountRequest request) {
        try {
            companyService.addOvertDiscount(request.username, companyId, request.fromDate, request.toDate, request.discountPercent);
            return ResponseEntity.ok(new CompanyResponse(true, "Overt discount added", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    @PostMapping("/{companyId}/discounts/conditional")
    public ResponseEntity<CompanyResponse> addConditionalDiscount(@PathVariable UUID companyId, @RequestBody DiscountRequest request) {
        try {
            companyService.addConditionalDiscount(request.username, companyId, request.fromDate, request.toDate, request.discountPercent, request.requiredTickets, request.appliedTickets);
            return ResponseEntity.ok(new CompanyResponse(true, "Conditional discount added", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    @PostMapping("/{companyId}/discounts/coupon")
    public ResponseEntity<CompanyResponse> addCouponCode(@PathVariable UUID companyId, @RequestBody DiscountRequest request) {
        try {
            companyService.addCouponCode(request.username, companyId, request.fromDate, request.toDate, request.discountPercent, request.code);
            return ResponseEntity.ok(new CompanyResponse(true, "Coupon code added", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    /**
     * דיוק 1: discountId עובר כ-PathVariable בנתיב.
     * מאחר ובקשת DELETE סטנדרטית לא רצוי שתכיל גוף (Body), אנחנו נעביר את ה-username כ-RequestParam.
     */
    @DeleteMapping("/{companyId}/discounts/{discountId}")
    public ResponseEntity<CompanyResponse> removeDiscount(
            @PathVariable UUID companyId,
            @PathVariable UUID discountId,
            @RequestParam String username) { // Username מגיע כמשתנה שאילתה (e.g., ?username=Admin)
        try {
            companyService.removeDiscount(username, companyId, discountId);
            return ResponseEntity.ok(new CompanyResponse(true, "Discount removed", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    @PostMapping("/{companyId}/rate")
    public ResponseEntity<CompanyResponse> rateCompany(@PathVariable UUID companyId, @RequestBody RateCompanyRequest request) {
        try {
            companyService.rateCompany(request.userId, companyId, request.rating);
            return ResponseEntity.ok(new CompanyResponse(true, "Company rated successfully", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping("/{companyId}/hierarchy")
    public ResponseEntity<CompanyResponse> getCompanyHierarchy(@PathVariable UUID companyId, @RequestParam String requesterUsername) {
        try {
            String mermaidData = companyService.getCompanyHierarchyMermaid(companyId, requesterUsername);
            return ResponseEntity.ok(new CompanyResponse(true, "Hierarchy fetched", mermaidData));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }

    @GetMapping("/{companyId}/sales-report")
    public ResponseEntity<CompanyResponse> getSalesReport(@PathVariable UUID companyId, @RequestParam String ownerUsername) {
        try {
            String reportData = companyService.getSalesReportForOwner(ownerUsername, companyId);
            return ResponseEntity.ok(new CompanyResponse(true, "Sales report fetched", reportData));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new CompanyResponse(false, e.getMessage(), null));
        }
    }
}