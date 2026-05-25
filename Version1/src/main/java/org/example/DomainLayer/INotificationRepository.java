package org.example.DomainLayer;

import org.example.DomainLayer.NotificationAggregate.Notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface INotificationRepository {

    void save(Notification notification);

    Optional<Notification> findById(UUID notificationId);

    List<Notification> findAllByRecipient(String recipientId);

    List<Notification> findUnreadByRecipient(String recipientId);

    void markAsRead(String recipientId, UUID notificationId);

    int markAllAsRead(String recipientId);
}