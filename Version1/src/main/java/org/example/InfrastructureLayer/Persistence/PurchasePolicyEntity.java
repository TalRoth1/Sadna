package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "purchase_policies")
public class PurchasePolicyEntity {

    @Id
    private UUID id;

    protected PurchasePolicyEntity() {
    }

    public PurchasePolicyEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}