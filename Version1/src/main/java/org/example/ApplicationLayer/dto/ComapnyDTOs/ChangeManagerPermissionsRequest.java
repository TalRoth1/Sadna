package org.example.ApplicationLayer.dto.ComapnyDTOs;

import java.util.Set;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

public class ChangeManagerPermissionsRequest {
    public String ownerUsername;
    public String managerUsername;
    public Set<CompanyPermission> newPermissions;
}
