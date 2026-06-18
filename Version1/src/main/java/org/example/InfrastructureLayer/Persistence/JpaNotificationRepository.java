package org.example.InfrastructureLayer.Persistence;

import org.example.DomainLayer.INotificationRepository;
import org.example.DomainLayer.NotificationAggregate.Notification;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Profile("localdb")
@Transactional
public class JpaNotificationRepository implements INotificationRepository {

    private final SpringDataNotificationRepository notificationJpa;

    public JpaNotificationRepository(SpringDataNotificationRepository notificationJpa) {
        this.notificationJpa = notificationJpa;
    }

    @Override
    public void save(Notification notification) {
        if (notification == null) {
            throw new IllegalArgumentException("Notification is required");
        }
        notificationJpa.save(toEntity(notification));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Notification> findById(UUID notificationId) {
        if (notificationId == null) {
            throw new IllegalArgumentException("Notification ID is required");
        }
        return notificationJpa.findById(notificationId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findAllByRecipient(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new IllegalArgumentException("Recipient ID is required");
        }
        return notificationJpa
                .findByRecipientIdOrderByCreatedAtDesc(UUID.fromString(recipientId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> findUnreadByRecipient(String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            throw new IllegalArgumentException("Recipient ID is required");
        }
        return notificationJpa
                .findByRecipientIdAndIsReadFalseOrderByCreatedAtAsc(UUID.fromString(recipientId))
                .stream()
                .map(this::toDomain)
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
        List<Notification> unread = findUnreadByRecipient(recipientId);

        for (Notification n : unread) {
            n.markAsRead();
            save(n);
        }

        return unread.size();
    }

    private NotificationEntity toEntity(Notification n) {
        return new NotificationEntity(
                n.getId(),
                UUID.fromString(n.getRecipientId()),
                n.getType(),
                n.getMessage(),
                n.getTargetUrl(),
                n.isRead(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }

    private Notification toDomain(NotificationEntity e) {
        return new Notification(
                e.getId(),
                e.getRecipientId().toString(),
                e.getNotificationType(),
                e.getMessage(),
                e.getTargetUrl(),
                e.getCreatedAt(),
                e.isRead(),
                e.getReadAt()
        );
    }
}