package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;
import org.example.DomainLayer.NotificationAggregate.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "target_url")
    private String targetUrl;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected NotificationEntity() {
    }

    public NotificationEntity(UUID id,
                               UUID recipientId,
                               NotificationType notificationType,
                               String message,
                               String targetUrl,
                               boolean isRead,
                               LocalDateTime readAt,
                               LocalDateTime createdAt) {
        this.id = id;
        this.recipientId = recipientId;
        this.notificationType = notificationType;
        this.message = message;
        this.targetUrl = targetUrl;
        this.isRead = isRead;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getRecipientId() { return recipientId; }
    public NotificationType getNotificationType() { return notificationType; }
    public String getMessage() { return message; }
    public String getTargetUrl() { return targetUrl; }
    public boolean isRead() { return isRead; }
    public LocalDateTime getReadAt() { return readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}