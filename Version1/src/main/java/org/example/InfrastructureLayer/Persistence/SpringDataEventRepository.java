package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataEventRepository extends JpaRepository<EventEntity, UUID> {
    List<EventEntity> findByCompanyId(UUID companyId);
}
