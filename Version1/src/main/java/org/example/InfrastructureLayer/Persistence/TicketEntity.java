package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.example.DomainLayer.EventAggregate.TicketStatus;

import java.util.UUID;

@Entity
@Table(name = "tickets")
public class TicketEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "seat_id")
    private UUID seatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status;

    @Column(name = "price", nullable = false)
    private double price;

    @Column(name = "active_purchase_id")
    private UUID activePurchaseId;

    @Column(name = "purchase_history_id")
    private UUID purchaseHistoryId;

    protected TicketEntity() {
    }

    public TicketEntity(UUID id,
                        UUID eventId,
                        UUID areaId,
                        UUID userId,
                        UUID seatId,
                        TicketStatus status,
                        double price,
                        UUID activePurchaseId,
                        UUID purchaseHistoryId) {
        this.id = id;
        this.eventId = eventId;
        this.areaId = areaId;
        this.userId = userId;
        this.seatId = seatId;
        this.status = status;
        this.price = price;
        this.activePurchaseId = activePurchaseId;
        this.purchaseHistoryId = purchaseHistoryId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSeatId() {
        return seatId;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public double getPrice() {
        return price;
    }
}
