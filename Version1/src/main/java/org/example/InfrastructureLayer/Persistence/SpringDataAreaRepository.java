package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataAreaRepository extends JpaRepository<AreaEntity, UUID> {
    List<AreaEntity> findByLayoutId(UUID layoutId);
}
