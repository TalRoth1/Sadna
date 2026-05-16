package org.example.InfrastructureLayer;

import org.example.DomainLayer.NotificationAggregate.INotifier;

public class WebSocketNotificationSender implements INotifier {

    private final Broadcaster broadcaster;

    public WebSocketNotificationSender(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public boolean notifyUser(String userId, String message) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Notification message is required");
        }

        return broadcaster.broadcast(userId.toString(), message);
    }
}