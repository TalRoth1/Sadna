package org.example.DomainLayer;

import org.example.DomainLayer.NotificationAggregate.Notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface INotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID notificationId);

    List<Notification> findUnreadByRecipient(String recipient);

    List<Notification> findAllByRecipient(String recipient);

    void markAllAsRead(String recipient);
}