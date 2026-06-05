package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataAdminActionLogRepository
        extends JpaRepository<AdminActionLogEntity, UUID> {

    List<AdminActionLogEntity> findAllByOrderByCreatedAtDesc();
}