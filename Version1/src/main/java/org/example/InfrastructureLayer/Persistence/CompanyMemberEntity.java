package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "company_members")
public class CompanyMemberEntity {

    @EmbeddedId
    private CompanyMemberId id;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "appointer_username")
    private String appointerUsername;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", nullable = false, columnDefinition = "jsonb")
    private Set<CompanyPermission> permissions = new HashSet<>();

    protected CompanyMemberEntity() {
    }

    public CompanyMemberEntity(UUID companyId,
                               String username,
                               String role,
                               String appointerUsername,
                               Set<CompanyPermission> permissions) {
        this.id = new CompanyMemberId(companyId, username);
        this.role = role;
        this.appointerUsername = appointerUsername;

        if (permissions != null) {
            this.permissions = new HashSet<>(permissions);
        }
    }

    public UUID getCompanyId() {
        return id.getCompanyId();
    }

    public String getUsername() {
        return id.getUsername();
    }

    public String getRole() {
        return role;
    }

    public String getAppointerUsername() {
        return appointerUsername;
    }

    public Set<CompanyPermission> getPermissions() {
        return permissions;
    }
}