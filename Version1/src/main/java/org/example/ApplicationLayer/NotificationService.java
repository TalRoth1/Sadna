package org.example.ApplicationLayer;

import java.util.logging.Logger;

import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final Logger logger = Logger.getLogger(NotificationService.class.getName());
    private final INotifier notifier;

    public NotificationService(INotifier notifier) {
        if (notifier == null) {
            logger.severe("Failed to initialize NotificationService: Notifier is null");
            throw new IllegalArgumentException("Notifier is required");
        }
        this.notifier = notifier;
    }

    public boolean notifyUser(String userId, String message) {
        if (userId == null) {
            logger.warning("Failed to notify user: User ID is null");
            throw new IllegalArgumentException("User ID is required");
        }

        if (message == null || message.isBlank()) {
            logger.warning("Failed to notify user: Notification message is null or blank");
            throw new IllegalArgumentException("Notification message is required");
        }

        boolean result = notifier.notifyUser(userId, message);
        if (!result) {
            logger.info("No listeners found for user ID: " + userId);
        }
        logger.info("Notification sent to user ID: " + userId + " with message: " + message);
        return result;
    }
}
