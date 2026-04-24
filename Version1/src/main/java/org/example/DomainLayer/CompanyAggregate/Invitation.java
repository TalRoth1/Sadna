package org.example.DomainLayer.CompanyAggregate;

import java.util.UUID;

public abstract class Invitation {
    private final UUID id;
    private final String appointerUsername;
    private final String appointeeUsername;
    private final UUID companyId;

    public Invitation(String appointerUsername, String appointeeUsername, UUID companyId) {
        this.id = UUID.randomUUID();
        this.appointerUsername = appointerUsername;
        this.appointeeUsername = appointeeUsername;
        this.companyId = companyId;
    }

    public UUID getId() {
        return id;
    }
    public String getAppointerUsername() {
        return appointerUsername;
    }

    public String getAppointeeUsername() {
        return appointeeUsername;
    }

    public UUID getCompanyId() {
        return companyId;
    }
}
