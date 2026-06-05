package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;
import org.example.DomainLayer.CompanyAggregate.CompanyPermission;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "company_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_members_company_username",
                        columnNames = {"companyId", "username"}
                )
        }
)
public class CompanyMemberEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String role;

    private String appointerUsername;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "company_member_permissions",
            joinColumns = @JoinColumn(name = "company_member_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private Set<CompanyPermission> permissions = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "company_member_events",
            joinColumns = @JoinColumn(name = "company_member_id")
    )
    @Column(name = "event_id")
    private Set<UUID> eventIds = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected CompanyMemberEntity() {
    }

    public CompanyMemberEntity(UUID companyId,
                               String username,
                               String role,
                               String appointerUsername,
                               Set<CompanyPermission> permissions,
                               Set<UUID> eventIds) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.username = username;
        this.role = role;
        this.appointerUsername = appointerUsername;
        if (permissions != null) {
            this.permissions = new HashSet<>(permissions);
        }
        if (eventIds != null) {
            this.eventIds = new HashSet<>(eventIds);
        }
        this.createdAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getUsername() {
        return username;
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

    public Set<UUID> getEventIds() {
        return eventIds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}