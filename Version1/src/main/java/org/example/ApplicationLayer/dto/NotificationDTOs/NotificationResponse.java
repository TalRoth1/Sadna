package org.example.ApplicationLayer.dto.NotificationDTOs;

import org.example.DomainLayer.NotificationAggregate.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public class NotificationResponse {
    public UUID notificationId;
    public String recipientUserId;
    public NotificationType type;
    public String title;
    public String message;
    public boolean read;
    public LocalDateTime createdAt;
    public LocalDateTime readAt;
    public String relatedEntityType;
    public UUID relatedEntityId;

    public NotificationResponse(
            UUID notificationId,
            String recipientUserId,
            NotificationType type,
            String title,
            String message,
            boolean read,
            LocalDateTime createdAt,
            LocalDateTime readAt,
            String relatedEntityType,
            UUID relatedEntityId
    ) {
        this.notificationId = notificationId;
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.read = read;
        this.createdAt = createdAt;
        this.readAt = readAt;
        this.relatedEntityType = relatedEntityType;
        this.relatedEntityId = relatedEntityId;
    }
}