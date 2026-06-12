package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataRuleRepository extends JpaRepository<RuleEntity, UUID> {

    Optional<RuleEntity> findByPolicyId(UUID policyId);

    void deleteByPolicyId(UUID policyId);
}