package org.example.API;

import java.util.List;
import java.util.UUID;

import org.example.ApplicationLayer.CompanyService;
import org.example.ApplicationLayer.JwtService;
import org.example.ApplicationLayer.dto.ApiResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.AddConditionalDiscountRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.AddCouponRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.AddOvertDiscountRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.AddPolicyRuleRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.ChangeManagerPermissionsRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.CloseCompanyRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.CompanyAccessResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.CompanyMembershipResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.CompanyResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.CreateCompanyRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.DeletePolicyRuleRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.HierarchyResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.InvitationResponse;
import org.example.ApplicationLayer.dto.CompanyDTOs.InviteManagerRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.InviteOwnerRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.RateCompanyRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.RemoveDiscountRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.RemoveMemberOwnerRequest;
import org.example.ApplicationLayer.dto.CompanyDTOs.SalesReportResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;

/**
 * CompanyController
 *
 * Every endpoint returns ResponseEntity<ApiResponse<T>> with a consistent shape:
 *   { "success": boolean, "message": string, "data": T | null }
 */
@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final JwtService jwtService;

    public CompanyController(CompanyService companyService, JwtService jwtService) {
        this.companyService = companyService;
        this.jwtService = jwtService;
    }

    // ================================================================
    //  Company creation & closing
    // ================================================================

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
            @RequestBody CreateCompanyRequest request) {
        try {
            CompanyResponse company = companyService.createCompany(
                    request.founderEmail, request.companyName);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Company created successfully", company));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/me/companies")
    public ResponseEntity<ApiResponse<List<CompanyMembershipResponse>>> getMyCompanies(
            HttpServletRequest request) {
        try {
            String email = resolveEmail(request);
            List<CompanyMembershipResponse> memberships = companyService.getUserCompanies(email);
            return ResponseEntity.ok(ApiResponse.<List<CompanyMembershipResponse>>success(memberships));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to load user companies"));
        }
    }

    @GetMapping("/me/invitations")
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> getMyInvitations(
            @RequestParam String userEmail) {
        try {
            List<InvitationResponse> invitations = companyService.getUserInvitations(userEmail);
            return ResponseEntity.ok(ApiResponse.success("User invitations loaded successfully", invitations));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to load user invitations"));
        }
    }

    @GetMapping("/{companyId}/permissions")
    public ResponseEntity<ApiResponse<CompanyAccessResponse>> getCompanyPermissions(
            @PathVariable("companyId") UUID companyId,
            @RequestParam String userEmail) {
        try {
            CompanyAccessResponse access = companyService.getCompanyAccess(companyId, userEmail);
            return ResponseEntity.ok(ApiResponse.success("Company permissions loaded successfully", access));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to load company permissions"));
        }
    }

    private String resolveEmail(HttpServletRequest request) {
        String emailParam = request.getParameter("userEmail");
        if (emailParam != null && !emailParam.isBlank()) {
            return emailParam;
        }

        String authHeader = request.getHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring("Bearer ".length()).trim();
        }

        Claims claims = (token == null) ? null : jwtService.parseAllowingExpired(token);
        if (claims == null) {
            return null;
        }

        Object emailClaim = claims.get("email");
        return emailClaim == null ? null : emailClaim.toString();
    }

    @DeleteMapping("/{companyId}/admin")
    public ResponseEntity<ApiResponse<Void>> closeCompanyAsAdmin(
            @PathVariable("companyId") UUID companyId,
            @RequestBody CloseCompanyRequest request) {
        try {
            companyService.closeCompanyAsAdmin(request.adminUsername, companyId);
            return ResponseEntity.ok(ApiResponse.success("Company closed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to close this company"));
        }
    }

    // ================================================================
    //  Invitations
    // ================================================================

    @PostMapping("/{companyId}/managers/invite")
    public ResponseEntity<ApiResponse<InvitationResponse>> inviteManager(
            @PathVariable("companyId") UUID companyId,
            @RequestBody InviteManagerRequest request) {
        try {
            InvitationResponse invitation = companyService.inviteCompanyManager(
                    request.ownerUsername, companyId,
                    request.usernameToInvite, request.permissions);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Manager invited successfully", invitation));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to invite managers"));
        }
    }

    @PostMapping("/{companyId}/owners/invite")
    public ResponseEntity<ApiResponse<InvitationResponse>> inviteOwner(
            @PathVariable("companyId") UUID companyId,
            @RequestBody InviteOwnerRequest request) {
        try {
            InvitationResponse invitation = companyService.inviteCompanyOwner(
                    request.ownerUsername, companyId, request.usernameToInvite);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Owner invited successfully", invitation));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to invite owners"));
        }
    }

    @PostMapping("/{companyId}/invitations/{invitationId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptInvitation(
            @PathVariable("companyId") UUID companyId,
            @PathVariable("invitationId") UUID invitationId,
            @RequestParam String username) {
        try {
            companyService.acceptCompanyInvitation(invitationId, username, companyId);
            return ResponseEntity.ok(ApiResponse.success("Invitation accepted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Invitation not found or already used"));
        }
    }

    @PostMapping("/{companyId}/invitations/{invitationId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectInvitation(
            @PathVariable("companyId") UUID companyId,
            @PathVariable("invitationId") UUID invitationId,
            @RequestParam String username) {
        try {
            companyService.rejectCompanyInvitation(invitationId, username, companyId);
            return ResponseEntity.ok(ApiResponse.success("Invitation rejected successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Invitation not found or already used"));
        }
    }

    // ================================================================
    //  Member management
    // ================================================================

    @DeleteMapping("/{companyId}/members/owner")
    public ResponseEntity<ApiResponse<Void>> removeMemberAsOwner(
            @PathVariable("companyId") UUID companyId,
            @RequestBody RemoveMemberOwnerRequest request) {
        try {
            companyService.removeCompanyMemberAsOwner(
                    request.ownerUsername, companyId, request.usernameToRemove);
            return ResponseEntity.ok(ApiResponse.success("Member removed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to remove this member"));
        }
    }


    @PatchMapping("/{companyId}/managers/permissions")
    public ResponseEntity<ApiResponse<Void>> changeManagerPermissions(
            @PathVariable("companyId") UUID companyId,
            @RequestBody ChangeManagerPermissionsRequest request) {
        try {
            companyService.changeManagerPermissions(
                    request.ownerUsername, companyId,
                    request.managerUsername, request.newPermissions);
            return ResponseEntity.ok(ApiResponse.success("Permissions updated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to change permissions"));
        }
    }

    // ================================================================
    //  Purchase Policy
    // ================================================================

    @PostMapping("/{companyId}/policy")
    public ResponseEntity<ApiResponse<Void>> addPolicyRule(
            @PathVariable("companyId") UUID companyId,
            @RequestBody AddPolicyRuleRequest request) {
        try {
            // No more Optional.ofNullable wrapping — pass nullable fields directly.
            companyService.addPolicyRule(
                    request.username, companyId,
                    request.age,
                    request.minTicket,
                    request.maxTicket,
                    request.allowLoneSeat);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Policy rule added successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to modify purchase policy"));
        }
    }

    @DeleteMapping("/{companyId}/policy/{ruleId}")
    public ResponseEntity<ApiResponse<Void>> deletePolicyRule(
            @PathVariable("companyId") UUID companyId,
            @PathVariable("ruleId") UUID ruleId,
            @RequestBody DeletePolicyRuleRequest request) {
        try {
            companyService.deletePolicyRule(request.username, companyId, ruleId);
            return ResponseEntity.ok(ApiResponse.success("Policy rule deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to modify purchase policy"));
        }
    }

    // ================================================================
    //  Discounts
    // ================================================================

    @PostMapping("/{companyId}/discounts/overt")
    public ResponseEntity<ApiResponse<Void>> addOvertDiscount(
            @PathVariable("companyId") UUID companyId,
            @RequestBody AddOvertDiscountRequest request) {
        try {
            companyService.addOvertDiscount(
                    request.username, companyId,
                    request.fromDate, request.toDate, request.discountPercent);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Overt discount added successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to add discounts"));
        }
    }

    @PostMapping("/{companyId}/discounts/conditional")
    public ResponseEntity<ApiResponse<Void>> addConditionalDiscount(
            @PathVariable("companyId") UUID companyId,
            @RequestBody AddConditionalDiscountRequest request) {
        try {
            companyService.addConditionalDiscount(
                    request.username, companyId,
                    request.fromDate, request.toDate, request.discountPercent,
                    request.requiredTickets, request.appliedTickets);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Conditional discount added successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to add discounts"));
        }
    }

    @PostMapping("/{companyId}/discounts/coupon")
    public ResponseEntity<ApiResponse<Void>> addCouponCode(
            @PathVariable("companyId") UUID companyId,
            @RequestBody AddCouponRequest request) {
        try {
            companyService.addCouponCode(
                    request.username, companyId,
                    request.fromDate, request.toDate,
                    request.discountPercent, request.code);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Coupon added successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to add coupons"));
        }
    }

    @DeleteMapping("/{companyId}/discounts/{discountId}")
    public ResponseEntity<ApiResponse<Void>> removeDiscount(
            @PathVariable("companyId") UUID companyId,
            @PathVariable("discountId") UUID discountId,
            @RequestBody RemoveDiscountRequest request) {
        try {
            companyService.removeDiscount(request.username, companyId, discountId);
            return ResponseEntity.ok(ApiResponse.success("Discount removed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to remove discounts"));
        }
    }

    // ================================================================
    //  Rating & Info
    // ================================================================

    @PostMapping("/{companyId}/ratings")
    public ResponseEntity<ApiResponse<Void>> rateCompany(
            @PathVariable("companyId") UUID companyId,
            @RequestBody RateCompanyRequest request) {
        try {
            companyService.rateCompany(request.userId, companyId, request.rating);
            return ResponseEntity.ok(ApiResponse.success("Rating submitted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/{companyId}/hierarchy")
    public ResponseEntity<ApiResponse<HierarchyResponse>> getCompanyHierarchy(
            @PathVariable("companyId") UUID companyId,
            @RequestParam String requester) {
        try {
            HierarchyResponse hierarchy = companyService.getCompanyHierarchyMermaid(
                    companyId, requester);
            return ResponseEntity.ok(ApiResponse.success(hierarchy));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to view company hierarchy"));
        }
    }

    @GetMapping("/{companyId}/sales-report")
    public ResponseEntity<ApiResponse<SalesReportResponse>> getSalesReport(
            @PathVariable("companyId") UUID companyId,
            @RequestParam String owner) {
        try {
            SalesReportResponse report = companyService.getSalesReportForOwner(
                    owner, companyId);
            return ResponseEntity.ok(ApiResponse.success(report));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to view sales report"));
        }
    }
}