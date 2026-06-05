package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;
import org.example.DomainLayer.AdminAggregate.AdminComplaintStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "admin_complaints")
public class AdminComplaintEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID reporterUserId;

    @Column(nullable = false)
    private String reporterUsername;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminComplaintStatus status;

    @Column(length = 2000)
    private String adminResponse;

    private String responderAdminUsername;

    private LocalDateTime respondedAt;

    protected AdminComplaintEntity() {
    }

    public AdminComplaintEntity(UUID id,
                                UUID reporterUserId,
                                String reporterUsername,
                                String title,
                                String description,
                                LocalDateTime createdAt,
                                AdminComplaintStatus status,
                                String adminResponse,
                                String responderAdminUsername,
                                LocalDateTime respondedAt) {
        this.id = id;
        this.reporterUserId = reporterUserId;
        this.reporterUsername = reporterUsername;
        this.title = title;
        this.description = description;
        this.createdAt = createdAt;
        this.status = status;
        this.adminResponse = adminResponse;
        this.responderAdminUsername = responderAdminUsername;
        this.respondedAt = respondedAt;
    }

    public UUID getId() { return id; }
    public UUID getReporterUserId() { return reporterUserId; }
    public String getReporterUsername() { return reporterUsername; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public AdminComplaintStatus getStatus() { return status; }
    public String getAdminResponse() { return adminResponse; }
    public String getResponderAdminUsername() { return responderAdminUsername; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
}