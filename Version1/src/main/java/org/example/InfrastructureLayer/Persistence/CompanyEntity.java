package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;
import org.example.DomainLayer.CompanyAggregate.Company;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "companies")
public class CompanyEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "founder_username", nullable = false, length = 100)
    private String founderUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Company.CompanyStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "discount_policy_id")
    private UUID discountPolicyId;

    @Column(name = "purchase_policy_id")
    private UUID purchasePolicyId;

    protected CompanyEntity() {
    }

    public CompanyEntity(UUID id,
                         String name,
                         String founderUsername,
                         Company.CompanyStatus status,
                         UUID discountPolicyId,
                         UUID purchasePolicyId) {
        this.id = id;
        this.name = name;
        this.founderUsername = founderUsername;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.discountPolicyId = discountPolicyId;
        this.purchasePolicyId = purchasePolicyId;
    }

    public CompanyEntity(UUID id,
                         String name,
                         String founderUsername,
                         Company.CompanyStatus status,
                         LocalDateTime createdAt,
                         UUID discountPolicyId,
                         UUID purchasePolicyId) {
        this.id = id;
        this.name = name;
        this.founderUsername = founderUsername;
        this.status = status;
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.discountPolicyId = discountPolicyId;
        this.purchasePolicyId = purchasePolicyId;
    }

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFounderUsername() {
        return founderUsername;
    }

    public Company.CompanyStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public UUID getDiscountPolicyId() {
        return discountPolicyId;
    }

    public UUID getPurchasePolicyId() {
        return purchasePolicyId;
    }
}