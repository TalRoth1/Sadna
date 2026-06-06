package org.example.DomainLayer.UserAggregate;

import java.util.UUID;

public class OwnerInvitation extends Invitation {
    public OwnerInvitation(User appointerUser, User appointeeUser, UUID companyId) {
        super(appointerUser, appointeeUser, companyId);
    }
    public OwnerInvitation(UUID id, User appointerUser, User appointeeUser, UUID companyId) {
        super(id, appointerUser, appointeeUser, companyId);
    }

}
