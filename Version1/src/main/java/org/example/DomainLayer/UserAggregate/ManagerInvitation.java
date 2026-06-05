package org.example.DomainLayer.UserAggregate;

import java.util.Set;
import java.util.UUID;

import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

public class ManagerInvitation extends Invitation {
    Set<CompanyPermission> premissions;

    public ManagerInvitation(User appointerUser, User appointeeUser, UUID companyId, Set<CompanyPermission> premissions) {
        super(appointerUser, appointeeUser, companyId);
        this.premissions = premissions;
    }

    public Set<CompanyPermission> getPremissions() {
        return premissions;
    }

    public ManagerInvitation(UUID id,
                             User appointerUser,
                             User appointeeUser,
                             UUID companyId,
                             Set<CompanyPermission> premissions) {
        super(id, appointerUser, appointeeUser, companyId);
        this.premissions = premissions;
    }
}
