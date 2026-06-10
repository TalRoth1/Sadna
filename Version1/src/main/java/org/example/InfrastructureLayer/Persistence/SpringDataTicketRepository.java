package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataTicketRepository extends JpaRepository<TicketEntity, UUID> {
    List<TicketEntity> findByEventId(UUID eventId);
}
