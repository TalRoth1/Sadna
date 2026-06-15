package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataRuleRepository extends JpaRepository<RuleEntity, UUID> {

    Optional<RuleEntity> findByPolicyId(UUID policyId);

    List<RuleEntity> findByPolicyIdIn(Collection<UUID> policyIds);

    void deleteByPolicyId(UUID policyId);
}