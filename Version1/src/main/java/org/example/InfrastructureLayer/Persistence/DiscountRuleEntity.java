package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "discounts")
public class DiscountRuleEntity {

    @Id
    private UUID id;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "discount_type", nullable = false, length = 50)
    private String discountType;

    @Column(name = "from_date", nullable = false)
    private LocalDateTime fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDateTime toDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> parameters;

    protected DiscountRuleEntity() {
    }

    public DiscountRuleEntity(UUID id,
                               UUID policyId,
                               String discountType,
                               LocalDateTime fromDate,
                               LocalDateTime toDate,
                               Map<String, Object> parameters) {
        this.id = id;
        this.policyId = policyId;
        this.discountType = discountType;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.parameters = parameters;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public String getDiscountType() {
        return discountType;
    }

    public LocalDateTime getFromDate() {
        return fromDate;
    }

    public LocalDateTime getToDate() {
        return toDate;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }
}