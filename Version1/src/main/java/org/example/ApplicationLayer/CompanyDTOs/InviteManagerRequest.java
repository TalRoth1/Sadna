package org.example.ApplicationLayer.CompanyDTOs;

import java.util.Set;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

public class InviteManagerRequest {
    public String ownerUsername;
    public String usernameToInvite;
    public Set<CompanyPermission> permissions;
}
