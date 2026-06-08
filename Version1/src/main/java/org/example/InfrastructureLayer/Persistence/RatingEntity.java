package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ratings")
public class RatingEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "review")
    private String review;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RatingEntity() {
    }

    public RatingEntity(UUID id,
                        UUID userId,
                        UUID eventId,
                        UUID companyId,
                        int rating,
                        String review) {
        this.id = id;
        this.userId = userId;
        this.eventId = eventId;
        this.companyId = companyId;
        this.rating = rating;
        this.review = review;
        this.createdAt = LocalDateTime.now();
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

    public UUID getUserId() {
        return userId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public int getRating() {
        return rating;
    }

    public String getReview() {
        return review;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}