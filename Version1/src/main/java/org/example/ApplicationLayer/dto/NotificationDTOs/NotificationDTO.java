package org.example.ApplicationLayer.dto.NotificationDTOs;

import java.time.LocalDateTime;
import java.util.UUID;

import org.example.DomainLayer.NotificationAggregate.Notification;
import org.example.DomainLayer.NotificationAggregate.NotificationType;

public class NotificationDTO {
    public UUID id;
    public String recipientId;
    public NotificationType type;
    public String message;
    public String targetUrl;
    public LocalDateTime createdAt;
    public boolean read;
    public LocalDateTime readAt;

    public NotificationDTO() {}

    public NotificationDTO(Notification n) {
        this.id = n.getId();
        this.recipientId = n.getRecipientId();
        this.type = n.getType();
        this.message = n.getMessage();
        this.targetUrl = n.getTargetUrl();
        this.createdAt = n.getCreatedAt();
        this.read = n.isRead();
        this.readAt = n.getReadAt();
    }
}