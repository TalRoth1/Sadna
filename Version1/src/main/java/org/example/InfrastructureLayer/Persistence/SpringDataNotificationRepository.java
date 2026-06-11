package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataNotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    List<NotificationEntity> findByRecipientIdAndIsReadFalseOrderByCreatedAtAsc(UUID recipientId);
}