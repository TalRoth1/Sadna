package org.example.DomainLayer.NotificationAggregate;

import java.time.LocalDateTime;
import java.util.UUID;

public class Notification {

    private final UUID id;
    private final String recipientId;
    private final NotificationType type;
    private final String message;
    private final String targetUrl;
    private final LocalDateTime createdAt;

    private boolean read;
    private LocalDateTime readAt;

    public Notification(String recipientId,
                        NotificationType type,
                        String message,
                        String targetUrl) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new IllegalArgumentException("Recipient ID is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Notification message is required");
        }

        this.id = UUID.randomUUID();
        this.recipientId = recipientId;
        this.type = type == null ? NotificationType.GENERAL : type;
        this.message = message;
        this.targetUrl = targetUrl;
        this.createdAt = LocalDateTime.now();
        this.read = false;
        this.readAt = null;
    }

    public UUID getId() {
        return id;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void markAsRead() {
        if (!read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }
}