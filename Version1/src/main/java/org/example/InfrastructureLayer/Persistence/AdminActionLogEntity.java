package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admin_action_logs")
public class AdminActionLogEntity {

    @Id
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "admin_username", nullable = false)
    private String adminUsername;

    @Column(name = "action_type", nullable = false, columnDefinition = "text")
    private String action;

    @Column(name = "target_id", columnDefinition = "text")
    private String target;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected AdminActionLogEntity() {
    }

    public AdminActionLogEntity(UUID id,
                                UUID adminId,
                                String adminUsername,
                                String action,
                                String target,
                                LocalDateTime createdAt) {
        this.id = id;
        this.adminId = adminId;
        this.adminUsername = adminUsername;
        this.action = action;
        this.target = target;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAdminId() {
        return adminId;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}