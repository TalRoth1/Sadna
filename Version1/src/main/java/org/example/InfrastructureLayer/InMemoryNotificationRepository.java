package org.example.InfrastructureLayer;

import org.example.DomainLayer.INotificationRepository;
import org.example.DomainLayer.NotificationAggregate.Notification;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryNotificationRepository implements INotificationRepository {

    private final ConcurrentHashMap<UUID, Notification> notifications = new ConcurrentHashMap<>();

    @Override
    public void save(Notification notification) {
        if (notification == null) {
            throw new IllegalArgumentException("Notification is required");
        }
        notifications.put(notification.getNotificationId(), notification);
    }

    @Override
    public Optional<Notification> findById(UUID notificationId) {
        if (notificationId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(notifications.get(notificationId));
    }

    @Override
    public List<Notification> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        List<Notification> result = new ArrayList<>();

        for (Notification notification : notifications.values()) {
            if (notification.getRecipientUserId().equals(userId)) {
                result.add(notification);
            }
        }

        result.sort(Comparator.comparing(Notification::getCreatedAt).reversed());
        return result;
    }

    @Override
    public List<Notification> findUnreadByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        List<Notification> result = new ArrayList<>();

        for (Notification notification : notifications.values()) {
            if (notification.getRecipientUserId().equals(userId) && !notification.isRead()) {
                result.add(notification);
            }
        }

        result.sort(Comparator.comparing(Notification::getCreatedAt).reversed());
        return result;
    }

    @Override
    public long countUnreadByUserId(String userId) {
        return findUnreadByUserId(userId).size();
    }

    @Override
    public void update(Notification notification) {
        if (notification == null) {
            throw new IllegalArgumentException("Notification is required");
        }
        notifications.put(notification.getNotificationId(), notification);
    }
}