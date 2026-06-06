package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;
import org.example.DomainLayer.UserAggregate.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserEntity() {
    }

    public UserEntity(UUID id,
                      String username,
                      String passwordHash,
                      UserStatus status) {
        LocalDateTime now = LocalDateTime.now();

        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash == null ? "" : passwordHash;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UserEntity(UUID id,
                      String username,
                      String passwordHash,
                      UserStatus status,
                      LocalDateTime createdAt,
                      LocalDateTime updatedAt) {
        LocalDateTime now = LocalDateTime.now();

        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash == null ? "" : passwordHash;
        this.status = status;
        this.createdAt = createdAt == null ? now : createdAt;
        this.updatedAt = updatedAt == null ? now : updatedAt;
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
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}