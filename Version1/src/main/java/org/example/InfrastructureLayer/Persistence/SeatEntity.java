package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "seats")
public class SeatEntity {

    @Id
    private UUID id;

    @Column(name = "row", nullable = false)
    private int row;

    @Column(name = "number", nullable = false)
    private int number;

    protected SeatEntity() {
    }

    public SeatEntity(UUID id, int row, int number) {
        this.id = id;
        this.row = row;
        this.number = number;
    }

    public UUID getId() {
        return id;
    }

    public int getRow() {
        return row;
    }

    public int getNumber() {
        return number;
    }
}
