package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "discount_policies")
public class DiscountPolicyEntity {

    @Id
    private UUID id;

    protected DiscountPolicyEntity() {
    }

    public DiscountPolicyEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}