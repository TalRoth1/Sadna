package org.example.ApplicationLayer.dto.ComapnyDTOs;

import java.util.Set;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

public class InviteManagerRequest {
    public String ownerUsername;
    public String usernameToInvite;
    public Set<CompanyPermission> permissions;
}
