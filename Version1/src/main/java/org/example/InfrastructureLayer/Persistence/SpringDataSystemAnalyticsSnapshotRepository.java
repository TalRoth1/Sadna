package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataSystemAnalyticsSnapshotRepository
        extends JpaRepository<SystemAnalyticsSnapshotEntity, UUID> {

    List<SystemAnalyticsSnapshotEntity> findAllByOrderByCreatedAtDesc();
}