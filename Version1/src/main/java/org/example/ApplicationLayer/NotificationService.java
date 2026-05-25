package org.example.ApplicationLayer;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.ApplicationLayer.dto.NotificationDTOs.NotificationDTO;
import org.example.DomainLayer.INotificationRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.NotificationAggregate.NotificationType;
import org.example.InfrastructureLayer.Notifier;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger logger = Logger.getLogger(NotificationService.class.getName());

    private final INotifier notifier;
    private final INotificationRepository notificationRepository;

    public NotificationService(INotifier notifier,
                               INotificationRepository notificationRepository) {
        if (notifier == null) {
            throw new IllegalArgumentException("Notifier is required");
        }
        if (notificationRepository == null) {
            throw new IllegalArgumentException("Notification repository is required");
        }

        this.notifier = notifier;
        this.notificationRepository = notificationRepository;
    }

    public boolean notifyUser(UUID userId, String message) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        return notifyUser(userId.toString(), message);
    }

    public boolean notifyUser(String userId, String message) {
        validateUserAndMessage(userId, message);

        boolean deliveredNow = notifier.notifyUser(userId, message);

        if (!deliveredNow) {
            logger.info("Notification saved as delayed notification for user ID: " + userId);
        }

        return deliveredNow;
    }

    public boolean notifyUser(String userId,
                              NotificationType type,
                              String message,
                              String targetUrl) {
        validateUserAndMessage(userId, message);

        if (notifier instanceof Notifier concreteNotifier) {
            return concreteNotifier.notifyUser(userId, type, message, targetUrl);
        }

        return notifier.notifyUser(userId, message);
    }


    public List<NotificationDTO> getAllNotifications(String userId) {
        validateUserId(userId);

        return notificationRepository.findAllByRecipient(userId)
                .stream()
                .map(NotificationDTO::new)
                .toList();
    }

    public List<NotificationDTO> getUnreadNotifications(String userId) {
        validateUserId(userId);

        return notificationRepository.findUnreadByRecipient(userId)
                .stream()
                .map(NotificationDTO::new)
                .toList();
    }

    public void markAsRead(String userId, UUID notificationId) {
        validateUserId(userId);

        if (notificationId == null) {
            throw new IllegalArgumentException("Notification ID is required");
        }

        notificationRepository.markAsRead(userId, notificationId);
    }

    public int markAllAsRead(String userId) {
        validateUserId(userId);
        return notificationRepository.markAllAsRead(userId);
    }

    private void validateUserAndMessage(String userId, String message) {
        validateUserId(userId);

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Notification message is required");
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }
    }
}