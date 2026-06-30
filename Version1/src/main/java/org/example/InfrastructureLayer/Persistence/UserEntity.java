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

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "age", nullable = false)
    private int age;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserEntity() {
    }

    public UserEntity(UUID id,
                      String username,
                      String email,
                      String passwordHash,
                      UserStatus status,
                      int age) {
        LocalDateTime now = LocalDateTime.now();

        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash == null ? "" : passwordHash;
        this.status = status;
        this.age = age;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UserEntity(UUID id,
                      String username,
                      String email,
                      String passwordHash,
                      UserStatus status,
                      LocalDateTime createdAt,
                      LocalDateTime updatedAt,
                      int age) {
        LocalDateTime now = LocalDateTime.now();

        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash == null ? "" : passwordHash;
        this.status = status;
        this.age = age;
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

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash == null ? "" : passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public int getAge() {
        return age;
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