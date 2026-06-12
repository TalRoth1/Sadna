package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataPurchasePolicyRepository extends JpaRepository<PurchasePolicyEntity, UUID> {
}