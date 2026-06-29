package org.example.ApplicationLayer;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.example.ApplicationLayer.dto.NotificationDTOs.NotificationDTO;
import org.example.DomainLayer.INotificationRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.NotificationAggregate.NotificationType;
import org.example.InfrastructureLayer.Notifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
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
        String action = "NOTIFY_USER_BY_UUID";
        logInfo(userId == null ? null : userId.toString(), action, "Started.");

        try {
            if (userId == null) {
                throw new IllegalArgumentException("User ID is required");
            }

            boolean deliveredNow = notifyUser(userId.toString(), message);

            logInfo(userId.toString(), action, "Completed successfully. deliveredNow=" + deliveredNow);
            return deliveredNow;
        } catch (RuntimeException e) {
            logError(userId == null ? null : userId.toString(), action, "Failed.", e);
            throw e;
        }
    }

    public boolean notifyUser(String userId, String message) {
        String action = "NOTIFY_USER";
        logInfo(userId, action, "Started.");

        try {
            validateUserAndMessage(userId, message);

            boolean deliveredNow = notifier.notifyUser(userId, message);

            if (deliveredNow) {
                logInfo(userId, action, "Completed successfully. deliveredNow=true");
            } else {
                logInfo(userId, action, "Notification saved as delayed notification. deliveredNow=false");
            }

            return deliveredNow;
        } catch (RuntimeException e) {
            logError(userId, action, "Failed.", e);
            throw e;
        }
    }

    public boolean notifyUser(String userId,
                              NotificationType type,
                              String message,
                              String targetUrl) {
        String action = "NOTIFY_USER_WITH_TYPE";
        logInfo(userId, action, "Started. type=" + type + ", targetUrl=" + targetUrl);

        try {
            validateUserAndMessage(userId, message);

            boolean deliveredNow;

            if (notifier instanceof Notifier concreteNotifier) {
                deliveredNow = concreteNotifier.notifyUser(userId, type, message, targetUrl);
            } else {
                deliveredNow = notifier.notifyUser(userId, message);
            }

            logInfo(userId, action, "Completed successfully. deliveredNow=" + deliveredNow);
            return deliveredNow;
        } catch (RuntimeException e) {
            logError(userId, action, "Failed. type=" + type + ", targetUrl=" + targetUrl, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> getAllNotifications(String userId) {
        String action = "GET_ALL_NOTIFICATIONS";
        logInfo(userId, action, "Started.");

        try {
            validateUserId(userId);

            List<NotificationDTO> result = notificationRepository.findAllByRecipient(userId)
                    .stream()
                    .map(NotificationDTO::new)
                    .toList();

            logInfo(userId, action, "Completed successfully. resultSize=" + result.size());
            return result;
        } catch (RuntimeException e) {
            logError(userId, action, "Failed.", e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> getUnreadNotifications(String userId) {
        String action = "GET_UNREAD_NOTIFICATIONS";
        logInfo(userId, action, "Started.");

        try {
            validateUserId(userId);

            List<NotificationDTO> result = notificationRepository.findUnreadByRecipient(userId)
                    .stream()
                    .map(NotificationDTO::new)
                    .toList();

            logInfo(userId, action, "Completed successfully. resultSize=" + result.size());
            return result;
        } catch (RuntimeException e) {
            logError(userId, action, "Failed.", e);
            throw e;
        }
    }

    public void markAsRead(String userId, UUID notificationId) {
        String action = "MARK_NOTIFICATION_AS_READ";
        logInfo(userId, action, "Started. notificationId=" + notificationId);

        try {
            validateUserId(userId);

            if (notificationId == null) {
                throw new IllegalArgumentException("Notification ID is required");
            }

            notificationRepository.markAsRead(userId, notificationId);

            logInfo(userId, action, "Completed successfully. notificationId=" + notificationId);
        } catch (RuntimeException e) {
            logError(userId, action, "Failed. notificationId=" + notificationId, e);
            throw e;
        }
    }

    public int markAllAsRead(String userId) {
        String action = "MARK_ALL_NOTIFICATIONS_AS_READ";
        logInfo(userId, action, "Started.");

        try {
            validateUserId(userId);

            int updatedCount = notificationRepository.markAllAsRead(userId);

            logInfo(userId, action, "Completed successfully. updatedCount=" + updatedCount);
            return updatedCount;
        } catch (RuntimeException e) {
            logError(userId, action, "Failed.", e);
            throw e;
        }
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

    private void logInfo(String userId, String action, String message) {
        logger.info("userId=" + userId
                + ", action=" + action
                + ", status=INFO, message=" + message);
    }

    private void logError(String userId, String action, String message, RuntimeException e) {
        logger.log(
                Level.SEVERE,
                "userId=" + userId
                        + ", action=" + action
                        + ", status=ERROR, message=" + message
                        + ", error=" + e.getMessage(),
                e
        );
    }
}