package org.example.DomainLayer.CompanyAggregate;

import java.util.Set;
import java.util.UUID;

public class ManagerInvetation extends Invitation {
    Set<Premissions> premissions;

    public ManagerInvetation(String appointerUsername, String appointeeUsername, UUID companyId, Set<Premissions> premissions) {
        super(appointerUsername, appointeeUsername, companyId);
        this.premissions = premissions;
    }

    public Set<Premissions> getPremissions() {
        return premissions;
    }
}
