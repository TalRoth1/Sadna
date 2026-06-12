package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataDiscountRuleRepository extends JpaRepository<DiscountRuleEntity, UUID> {

    List<DiscountRuleEntity> findByPolicyId(UUID policyId);

    void deleteByPolicyId(UUID policyId);
}