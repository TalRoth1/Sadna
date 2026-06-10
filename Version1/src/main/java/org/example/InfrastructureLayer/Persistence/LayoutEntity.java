package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "layout")
public class LayoutEntity {

    @Id
    private UUID id;

    @Column(name = "map_image", nullable = false)
    private String mapImage;

    protected LayoutEntity() {
    }

    public LayoutEntity(UUID id, String mapImage) {
        this.id = id;
        this.mapImage = mapImage == null ? "" : mapImage;
    }

    public UUID getId() {
        return id;
    }

    public String getMapImage() {
        return mapImage;
    }
}
