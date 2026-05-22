package org.example.DomainLayer.NotificationAggregate;

import java.time.LocalDateTime;
import java.util.UUID;

public class Notification {

    private final UUID notificationId;
    private final String recipientUserId;
    private final NotificationType type;
    private final String title;
    private final String message;
    private final LocalDateTime createdAt;

    private boolean read;
    private LocalDateTime readAt;

    private final String relatedEntityType;
    private final UUID relatedEntityId;

    public Notification(
            String recipientUserId,
            NotificationType type,
            String title,
            String message,
            String relatedEntityType,
            UUID relatedEntityId
    ) {
        if (recipientUserId == null || recipientUserId.isBlank()) {
            throw new IllegalArgumentException("Recipient user ID is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Notification type is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Notification title is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Notification message is required");
        }

        this.notificationId = UUID.randomUUID();
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.createdAt = LocalDateTime.now();
        this.read = false;
        this.readAt = null;
        this.relatedEntityType = relatedEntityType;
        this.relatedEntityId = relatedEntityId;
    }

    public UUID getNotificationId() {
        return notificationId;
    }

    public String getRecipientUserId() {
        return recipientUserId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
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

    public String getRelatedEntityType() {
        return relatedEntityType;
    }

    public UUID getRelatedEntityId() {
        return relatedEntityId;
    }

    public void markAsRead() {
        if (!read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }
}