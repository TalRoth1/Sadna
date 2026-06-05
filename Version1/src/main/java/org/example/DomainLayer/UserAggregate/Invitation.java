package org.example.DomainLayer.UserAggregate;

import java.util.UUID;

public abstract class Invitation {
    private final UUID id;
    private final User appointerUser;
    private final User appointeeUser;
    private final UUID companyId;

    public Invitation(User appointerUser, User appointeeUser, UUID companyId) {
        this.id = UUID.randomUUID();
        this.appointerUser = appointerUser;
        this.appointeeUser = appointeeUser;
        this.companyId = companyId;
    }

    public UUID getId() {
        return id;
    }
    public User getAppointerUser() {
        return appointerUser;
    }

    public User getAppointeeUser() {
        return appointeeUser;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    protected Invitation(UUID id, User appointerUser, User appointeeUser, UUID companyId) {
        this.id = id;
        this.appointerUser = appointerUser;
        this.appointeeUser = appointeeUser;
        this.companyId = companyId;
    }
}
