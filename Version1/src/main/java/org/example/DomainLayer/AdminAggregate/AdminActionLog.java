package org.example.DomainLayer.AdminAggregate;

import java.time.LocalDateTime;
import java.util.UUID;

public class AdminActionLog {
    private final UUID id;
    private final UUID adminId;
    private final String adminUsername;
    private final String action;
    private final String target;
    private final LocalDateTime createdAt;

    public AdminActionLog(UUID adminId, String adminUsername, String action, String target) {
        if (adminId == null) {
            throw new IllegalArgumentException("Admin ID is required");
        }

        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Action is required");
        }

        this.id = UUID.randomUUID();
        this.adminId = adminId;
        this.adminUsername = adminUsername;
        this.action = action;
        this.target = target;
        this.createdAt = LocalDateTime.now();
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