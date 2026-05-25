package org.example.InfrastructureLayer;

import java.util.UUID;

import org.example.ApplicationLayer.dto.NotificationDTOs.NotificationDTO;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.NotificationAggregate.Notification;
import org.example.DomainLayer.NotificationAggregate.NotificationType;

public class WebSocketNotificationSender implements INotifier {

    private final Broadcaster broadcaster;

    public WebSocketNotificationSender(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public boolean notifyUser(UUID userId, String message) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        return notifyUser(userId.toString(), message);
    }

    @Override
    public boolean notifyUser(String userId, String message) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Notification message is required");
        }

        NotificationDTO dto = new NotificationDTO(
                new Notification(userId, NotificationType.GENERAL, message, null));
        return broadcaster.broadcast(userId, dto);
    }
}