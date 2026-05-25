package org.example.InfrastructureLayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.example.DomainLayer.INotificationRepository;
import org.example.DomainLayer.NotificationAggregate.Notification;


public class NotificationRepository implements INotificationRepository {

    private final ConcurrentHashMap<UUID, Notification> notifications = new ConcurrentHashMap<>();

    @Override
    public void save(Notification notification) {
        if (notification == null) {
            throw new IllegalArgumentException("Notification is required");
        }
        notifications.put(notification.getId(), notification);
    }

    @Override
    public Optional<Notification> findById(UUID notificationId) {
        if (notificationId == null) {
            throw new IllegalArgumentException("Notification ID is required");
        }
        return Optional.ofNullable(notifications.get(notificationId));
    }

    @Override
    public List<Notification> findAllByRecipient(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new IllegalArgumentException("Recipient ID is required");
        }

        return notifications.values().stream()
                .filter(n -> recipientId.equals(n.getRecipientId()))
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public List<Notification> findUnreadByRecipient(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new IllegalArgumentException("Recipient ID is required");
        }

        return notifications.values().stream()
                .filter(n -> recipientId.equals(n.getRecipientId()))
                .filter(n -> !n.isRead())
                .sorted(Comparator.comparing(Notification::getCreatedAt))
                .toList();
    }

    @Override
    public void markAsRead(String recipientId, UUID notificationId) {
        Notification notification = findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        if (!notification.getRecipientId().equals(recipientId)) {
            throw new IllegalArgumentException("Notification does not belong to this user");
        }

        notification.markAsRead();
        save(notification);
    }

    @Override
    public int markAllAsRead(String recipientId) {
        List<Notification> unread = new ArrayList<>(findUnreadByRecipient(recipientId));

        for (Notification n : unread) {
            n.markAsRead();
            save(n);
        }

        return unread.size();
    }
}