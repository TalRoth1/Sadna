package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.NotificationDTOs.NotificationResponse;
import org.example.ApplicationLayer.dto.NotificationDTOs.SendNotificationRequest;
import org.example.DomainLayer.INotificationRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.NotificationAggregate.Notification;
import org.example.DomainLayer.NotificationAggregate.NotificationType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger logger = Logger.getLogger(NotificationService.class.getName());

    private final INotifier notifier;
    private final INotificationRepository notificationRepository;

    public NotificationService(
            INotifier notifier,
            INotificationRepository notificationRepository
    ) {
        if (notifier == null) {
            logger.severe("Failed to initialize NotificationService: Notifier is null");
            throw new IllegalArgumentException("Notifier is required");
        }
        if (notificationRepository == null) {
            logger.severe("Failed to initialize NotificationService: Notification repository is null");
            throw new IllegalArgumentException("Notification repository is required");
        }

        this.notifier = notifier;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Backward-compatible method for existing code.
     * Creates a generic notification and tries to send it in real time.
     */
    public boolean notifyUser(String userId, String message) {
        NotificationResponse response = createAndNotify(
                userId,
                NotificationType.SYSTEM_OR_PRODUCER_MESSAGE,
                "Notification",
                message,
                null,
                null
        );

        return response != null;
    }

    public NotificationResponse createAndNotify(
            String recipientUserId,
            NotificationType type,
            String title,
            String message,
            String relatedEntityType,
            UUID relatedEntityId
    ) {
        validateCreateNotification(recipientUserId, type, title, message);

        Notification notification = new Notification(
                recipientUserId,
                type,
                title,
                message,
                relatedEntityType,
                relatedEntityId
        );

        notificationRepository.save(notification);

        boolean deliveredNow = notifier.notifyUser(recipientUserId, message);

        if (deliveredNow) {
            logger.info("Notification delivered in real time to user ID: " + recipientUserId);
        } else {
            logger.info("User is offline. Notification saved for delayed delivery. User ID: " + recipientUserId);
        }

        return toResponse(notification);
    }

    public NotificationResponse sendManualNotification(SendNotificationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Notification request is required");
        }

        NotificationType type = request.type == null
                ? NotificationType.SYSTEM_OR_PRODUCER_MESSAGE
                : request.type;

        return createAndNotify(
                request.recipientUserId,
                type,
                request.title,
                request.message,
                request.relatedEntityType,
                request.relatedEntityId
        );
    }

    public List<NotificationResponse> getNotificationsForUser(String userId) {
        validateUserId(userId);

        return notificationRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<NotificationResponse> getUnreadNotificationsForUser(String userId) {
        validateUserId(userId);

        return notificationRepository.findUnreadByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public long getUnreadCount(String userId) {
        validateUserId(userId);
        return notificationRepository.countUnreadByUserId(userId);
    }

    public void markAsRead(String userId, UUID notificationId) {
        validateUserId(userId);

        if (notificationId == null) {
            throw new IllegalArgumentException("Notification ID is required");
        }

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        if (!notification.getRecipientUserId().equals(userId)) {
            throw new IllegalArgumentException("Notification does not belong to this user");
        }

        notification.markAsRead();
        notificationRepository.update(notification);
    }

    public void markAllAsRead(String userId) {
        validateUserId(userId);

        List<Notification> unreadNotifications = notificationRepository.findUnreadByUserId(userId);

        for (Notification notification : unreadNotifications) {
            notification.markAsRead();
            notificationRepository.update(notification);
        }
    }

    private void validateCreateNotification(
            String recipientUserId,
            NotificationType type,
            String title,
            String message
    ) {
        validateUserId(recipientUserId);

        if (type == null) {
            throw new IllegalArgumentException("Notification type is required");
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Notification title is required");
        }

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Notification message is required");
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getRecipientUserId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.getRelatedEntityType(),
                notification.getRelatedEntityId()
        );
    }
}