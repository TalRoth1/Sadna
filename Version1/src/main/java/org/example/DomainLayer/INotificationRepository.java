package org.example.DomainLayer;

import org.example.DomainLayer.NotificationAggregate.Notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface INotificationRepository {

    void save(Notification notification);

    Optional<Notification> findById(UUID notificationId);

    List<Notification> findByUserId(String userId);

    List<Notification> findUnreadByUserId(String userId);

    long countUnreadByUserId(String userId);

    void update(Notification notification);
}