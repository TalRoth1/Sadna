package org.example.ApplicationLayer.dto.NotificationDTOs;

import org.example.DomainLayer.NotificationAggregate.NotificationType;

import java.util.UUID;

public class SendNotificationRequest {
    public String recipientUserId;
    public NotificationType type;
    public String title;
    public String message;
    public String relatedEntityType;
    public UUID relatedEntityId;
}