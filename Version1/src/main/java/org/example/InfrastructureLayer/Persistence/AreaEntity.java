package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "event_areas")
public class AreaEntity {

    @Id
    private UUID id;

    @Column(name = "layout_id", nullable = false)
    private UUID layoutId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "price", nullable = false)
    private double price;

    protected AreaEntity() {
    }

    public AreaEntity(UUID id, UUID layoutId, String type, double price) {
        this.id = id;
        this.layoutId = layoutId;
        this.type = type;
        this.price = price;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLayoutId() {
        return layoutId;
    }

    public String getType() {
        return type;
    }

    public double getPrice() {
        return price;
    }
}
