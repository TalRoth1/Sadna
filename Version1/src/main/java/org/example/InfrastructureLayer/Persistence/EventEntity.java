package org.example.InfrastructureLayer.Persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.example.DomainLayer.EventAggregate.EventStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "events")
public class EventEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "manager_username", nullable = false)
    private String managerUsername;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(name = "description")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "artist")
    private String artist;

    @Column(name = "type")
    private String type;

    @Column(name = "date", nullable = false)
    private LocalDateTime date;

    @Column(name = "rating")
    private Double rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "layout_id")
    private UUID layoutId;

    @Column(name = "lottery_id")
    private UUID lotteryId;

    @Column(name = "discount_policy_id")
    private UUID discountPolicyId;

    @Column(name = "purchase_policy_id")
    private UUID purchasePolicyId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected EventEntity() {
    }

    public EventEntity(UUID id,
                       String name,
                       UUID companyId,
                       String managerUsername,
                       String location,
                       String description,
                       List<String> tags,
                       String artist,
                       String type,
                       LocalDateTime date,
                       Double rating,
                       EventStatus status,
                       UUID layoutId,
                       UUID lotteryId,
                       UUID discountPolicyId,
                       UUID purchasePolicyId) {
        this.id = id;
        this.name = name;
        this.companyId = companyId;
        this.managerUsername = managerUsername == null || managerUsername.isBlank() ? "" : managerUsername;
        this.location = location;
        this.description = description;
        this.tags = tags;
        this.artist = artist;
        this.type = type;
        this.date = date;
        this.rating = rating;
        this.status = status;
        this.layoutId = layoutId;
        this.lotteryId = lotteryId;
        this.discountPolicyId = discountPolicyId;
        this.purchasePolicyId = purchasePolicyId;
        // Ensure non-null audit timestamps in case JPA lifecycle callbacks are not invoked
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getManagerUsername() {
        return managerUsername;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getTags() {
        return tags;
    }

    public String getArtist() {
        return artist;
    }

    public String getType() {
        return type;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public Double getRating() {
        return rating;
    }

    public EventStatus getStatus() {
        return status;
    }

    public UUID getLayoutId() {
        return layoutId;
    }

    public UUID getLotteryId() {
        return lotteryId;
    }

    public UUID getDiscountPolicyId() {
        return discountPolicyId;
    }

    public UUID getPurchasePolicyId() {
        return purchasePolicyId;
    }
}
