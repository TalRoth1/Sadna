package org.example.ApplicationLayer.dto.CompanyDTOs;

import java.util.List;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

/**
 * Payload for the company page.
 * Contains the company identity, the current user's role/status,
 * and the permissions granted to that user.
 */
public class CompanyAccessResponse {
    public final UUID companyId;
    public final String companyName;
    public final String userEmail;
    public final String role;
    public final String status;
    public final List<CompanyPermission> grantedPermissions;

    public CompanyAccessResponse(UUID companyId,
                                 String companyName,
                                 String userEmail,
                                 String role,
                                 String status,
                                 List<CompanyPermission> grantedPermissions) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.userEmail = userEmail;
        this.role = role;
        this.status = status;
        this.grantedPermissions = grantedPermissions;
    }
}