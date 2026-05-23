package org.example.ApplicationLayer.dto.CompanyDTOs;

import java.util.UUID;

/**
 * Membership payload for the My Companies page.
 * Includes the company identity, the current user's role, and the company status.
 */
public class CompanyMembershipResponse {
    public final UUID companyId;
    public final String companyName;
    public final String role;
    public final String status;

    public CompanyMembershipResponse(UUID companyId, String companyName, String role, String status) {
        this.companyId = companyId;
        this.companyName = companyName;
        this.role = role;
        this.status = status;
    }
}
