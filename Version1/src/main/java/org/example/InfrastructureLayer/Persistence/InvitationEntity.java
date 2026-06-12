package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "invitations")
public class InvitationEntity {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "appointer_username", nullable = false)
    private String appointerUsername;

    @Column(name = "apointee_username", nullable = false)
    private String apointeeUsername;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions", nullable = false, columnDefinition = "jsonb")
    private Set<CompanyPermission> permissions = new HashSet<>();

    protected InvitationEntity() {
    }

    public InvitationEntity(UUID id,
                            UUID companyId,
                            String appointerUsername,
                            String apointeeUsername,
                            String role,
                            Set<CompanyPermission> permissions) {
        this.id = id;
        this.companyId = companyId;
        this.appointerUsername = appointerUsername;
        this.apointeeUsername = apointeeUsername;
        this.role = role;

        if (permissions != null) {
            this.permissions = new HashSet<>(permissions);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getAppointerUsername() {
        return appointerUsername;
    }

    public String getApointeeUsername() {
        return apointeeUsername;
    }

    public String getRole() {
        return role;
    }

    public Set<CompanyPermission> getPermissions() {
        return permissions;
    }
}