package org.example.ApplicationLayer.dto.ComapnyDTOs;

import java.util.Set;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

/**
 * Payload for inviteManager / inviteOwner.
 * permissions is only populated for MANAGER invitations, null for OWNER.
 */
public class InvitationResponse {
    public final UUID invitationId;
    public final UUID companyId;
    public final String appointerUsername;
    public final String appointeeUsername;
    public final String invitationType;              // "MANAGER" or "OWNER"
    public final Set<CompanyPermission> permissions; // null for OWNER invitations

    public InvitationResponse(UUID invitationId, UUID companyId,
                              String appointerUsername, String appointeeUsername,
                              String invitationType, Set<CompanyPermission> permissions) {
        this.invitationId = invitationId;
        this.companyId = companyId;
        this.appointerUsername = appointerUsername;
        this.appointeeUsername = appointeeUsername;
        this.invitationType = invitationType;
        this.permissions = permissions;
    }
}
