package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CompanyMemberId implements Serializable {

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "username", nullable = false)
    private String username;

    protected CompanyMemberId() {
    }

    public CompanyMemberId(UUID companyId, String username) {
        this.companyId = companyId;
        this.username = username;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CompanyMemberId that)) return false;
        return Objects.equals(companyId, that.companyId)
                && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(companyId, username);
    }
}