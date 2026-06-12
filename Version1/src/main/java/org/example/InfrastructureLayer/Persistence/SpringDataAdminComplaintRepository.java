package org.example.InfrastructureLayer.Persistence;

import org.example.DomainLayer.AdminAggregate.AdminComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataAdminComplaintRepository
        extends JpaRepository<AdminComplaintEntity, UUID> {

    List<AdminComplaintEntity> findByStatus(AdminComplaintStatus status);
}