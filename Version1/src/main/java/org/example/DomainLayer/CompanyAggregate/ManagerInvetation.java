package org.example.DomainLayer.CompanyAggregate;

import java.util.Set;
import java.util.UUID;

public class ManagerInvetation extends Invitation {
    Set<CompanyPermission> premissions;

    public ManagerInvetation(String appointerUsername, String appointeeUsername, UUID companyId, Set<CompanyPermission> premissions) {
        super(appointerUsername, appointeeUsername, companyId);
        this.premissions = premissions;
    }

    public Set<CompanyPermission> getPremissions() {
        return premissions;
    }
}
