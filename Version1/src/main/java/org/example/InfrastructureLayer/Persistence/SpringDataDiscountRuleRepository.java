package org.example.InfrastructureLayer.Persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SpringDataDiscountRuleRepository extends JpaRepository<DiscountRuleEntity, UUID> {

    List<DiscountRuleEntity> findByPolicyId(UUID policyId);

    List<DiscountRuleEntity> findByPolicyIdIn(Collection<UUID> policyIds);

    void deleteByPolicyId(UUID policyId);
}