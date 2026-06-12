package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataHistoryRepository extends JpaRepository<PurchaseHistoryEntity, UUID> {

    List<PurchaseHistoryEntity> findByUserId(UUID userId);

    List<PurchaseHistoryEntity> findByEventId(UUID eventId);
}