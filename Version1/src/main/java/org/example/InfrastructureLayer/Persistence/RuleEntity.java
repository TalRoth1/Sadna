package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "rules")
public class RuleEntity {

    @Id
    private UUID id;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "rule_type", nullable = false, length = 50)
    private String ruleType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    protected RuleEntity() {
    }

    public RuleEntity(UUID id, UUID policyId, String ruleType, Map<String, Object> parameters) {
        this.id = id;
        this.policyId = policyId;
        this.ruleType = ruleType;
        this.parameters = parameters;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public String getRuleType() {
        return ruleType;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }
}