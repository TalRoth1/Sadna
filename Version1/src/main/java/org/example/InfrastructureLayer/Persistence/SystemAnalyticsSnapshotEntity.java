package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "system_analytics_snapshots")
public class SystemAnalyticsSnapshotEntity {

    @Id
    private UUID id;

    private int registeredUsersCount;
    private int loggedInUsersCount;
    private int activeCompaniesCount;
    private int activeQueuesCount;
    private int activePurchasesCount;
    private int totalPurchasesCount;

    private double newSubscriberRatePerMin;
    private double ticketReservationRatePerMin;
    private double ticketPurchaseRatePerMin;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected SystemAnalyticsSnapshotEntity() {
    }

    public SystemAnalyticsSnapshotEntity(UUID id,
                                         int registeredUsersCount,
                                         int loggedInUsersCount,
                                         int activeCompaniesCount,
                                         int activeQueuesCount,
                                         int activePurchasesCount,
                                         int totalPurchasesCount,
                                         double newSubscriberRatePerMin,
                                         double ticketReservationRatePerMin,
                                         double ticketPurchaseRatePerMin,
                                         LocalDateTime createdAt) {
        this.id = id;
        this.registeredUsersCount = registeredUsersCount;
        this.loggedInUsersCount = loggedInUsersCount;
        this.activeCompaniesCount = activeCompaniesCount;
        this.activeQueuesCount = activeQueuesCount;
        this.activePurchasesCount = activePurchasesCount;
        this.totalPurchasesCount = totalPurchasesCount;
        this.newSubscriberRatePerMin = newSubscriberRatePerMin;
        this.ticketReservationRatePerMin = ticketReservationRatePerMin;
        this.ticketPurchaseRatePerMin = ticketPurchaseRatePerMin;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public int getRegisteredUsersCount() { return registeredUsersCount; }
    public int getLoggedInUsersCount() { return loggedInUsersCount; }
    public int getActiveCompaniesCount() { return activeCompaniesCount; }
    public int getActiveQueuesCount() { return activeQueuesCount; }
    public int getActivePurchasesCount() { return activePurchasesCount; }
    public int getTotalPurchasesCount() { return totalPurchasesCount; }
    public double getNewSubscriberRatePerMin() { return newSubscriberRatePerMin; }
    public double getTicketReservationRatePerMin() { return ticketReservationRatePerMin; }
    public double getTicketPurchaseRatePerMin() { return ticketPurchaseRatePerMin; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}