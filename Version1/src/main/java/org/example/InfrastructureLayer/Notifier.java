package org.example.InfrastructureLayer;

import java.util.UUID;

import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.NotificationAggregate.Notification;
import org.example.DomainLayer.NotificationAggregate.NotificationType;
import org.springframework.stereotype.Component;

public class Notifier implements INotifier {

    private final Broadcaster broadcaster;
    private final NotificationRepository notificationRepository;

    public Notifier(Broadcaster broadcaster,
                    NotificationRepository notificationRepository) {
        if (broadcaster == null) {
            throw new IllegalArgumentException("Broadcaster is required");
        }
        if (notificationRepository == null) {
            throw new IllegalArgumentException("Notification repository is required");
        }

        this.broadcaster = broadcaster;
        this.notificationRepository = notificationRepository;
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
        return notifyUser(userId, NotificationType.GENERAL, message, null);
    }

    public boolean notifyUser(String userId,
                              NotificationType type,
                              String message,
                              String targetUrl) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Notification message is required");
        }

        Notification notification = new Notification(userId, type, message, targetUrl);
        notificationRepository.save(notification);

        return broadcaster.broadcast(userId, message);
    }
}