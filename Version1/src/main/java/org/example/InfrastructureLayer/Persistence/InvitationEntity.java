package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "invitations")
public class InvitationEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String appointerUsername;

    @Column(nullable = false)
    private String appointeeUsername;

    @Column(nullable = false)
    private String type;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "invitation_permissions",
            joinColumns = @JoinColumn(name = "invitation_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private Set<CompanyPermission> permissions = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected InvitationEntity() {
    }

    public InvitationEntity(UUID id,
                            UUID companyId,
                            String appointerUsername,
                            String appointeeUsername,
                            String type,
                            Set<CompanyPermission> permissions) {
        this.id = id;
        this.companyId = companyId;
        this.appointerUsername = appointerUsername;
        this.appointeeUsername = appointeeUsername;
        this.type = type;
        if (permissions != null) {
            this.permissions = new HashSet<>(permissions);
        }
        this.createdAt = LocalDateTime.now();
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

    public String getAppointeeUsername() {
        return appointeeUsername;
    }

    public String getType() {
        return type;
    }

    public Set<CompanyPermission> getPermissions() {
        return permissions;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}