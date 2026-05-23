package org.example.DomainLayer.AdminAggregate;

import java.time.LocalDateTime;
import java.util.UUID;

public class AdminComplaint {
    private final UUID id;
    private final UUID reporterUserId;
    private final String reporterUsername;
    private final String title;
    private final String description;
    private final LocalDateTime createdAt;

    private AdminComplaintStatus status;
    private String adminResponse;
    private String responderAdminUsername;
    private LocalDateTime respondedAt;

    public AdminComplaint(UUID reporterUserId, String reporterUsername, String title, String description) {
        if (reporterUserId == null) {
            throw new IllegalArgumentException("Reporter user ID is required");
        }

        if (reporterUsername == null || reporterUsername.isBlank()) {
            throw new IllegalArgumentException("Reporter username is required");
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Complaint title is required");
        }

        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Complaint description is required");
        }

        this.id = UUID.randomUUID();
        this.reporterUserId = reporterUserId;
        this.reporterUsername = reporterUsername;
        this.title = title;
        this.description = description;
        this.createdAt = LocalDateTime.now();
        this.status = AdminComplaintStatus.OPEN;
    }

    public void respond(String adminUsername, String response) {
        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Response is required");
        }

        this.adminResponse = response;
        this.responderAdminUsername = adminUsername;
        this.respondedAt = LocalDateTime.now();
        this.status = AdminComplaintStatus.ANSWERED;
    }

    public void close() {
        this.status = AdminComplaintStatus.CLOSED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReporterUserId() {
        return reporterUserId;
    }

    public String getReporterUsername() {
        return reporterUsername;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public AdminComplaintStatus getStatus() {
        return status;
    }

    public String getAdminResponse() {
        return adminResponse;
    }

    public String getResponderAdminUsername() {
        return responderAdminUsername;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }
}