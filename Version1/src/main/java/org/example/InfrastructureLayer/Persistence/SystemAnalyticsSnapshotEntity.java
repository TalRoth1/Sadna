package org.example.InfrastructureLayer.Persistence;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "system_analytics_snapshots")
public class SystemAnalyticsSnapshotEntity {

    @Id
    private UUID id;

    @Column(name = "registered_users_count", nullable = false)
    private int registeredUsersCount;

    @Column(name = "logged_in_users_count", nullable = false)
    private int loggedInUsersCount;

    @Column(name = "active_companies_count", nullable = false)
    private int activeCompaniesCount;

    @Column(name = "active_queues_count", nullable = false)
    private int activeQueuesCount;

    @Column(name = "active_purchases_count", nullable = false)
    private int activePurchasesCount;

    @Column(name = "total_purchases_count", nullable = false)
    private int totalPurchasesCount;

    @Column(name = "new_subscriber_rate_per_min", nullable = false)
    private double newSubscriberRatePerMin;

    @Column(name = "ticket_reservation_rate_per_min", nullable = false)
    private double ticketReservationRatePerMin;

    @Column(name = "ticket_purchase_rate_per_min", nullable = false)
    private double ticketPurchaseRatePerMin;

    @Column(name = "created_at", nullable = false)
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

    public UUID getId() {
        return id;
    }

    public int getRegisteredUsersCount() {
        return registeredUsersCount;
    }

    public int getLoggedInUsersCount() {
        return loggedInUsersCount;
    }

    public int getActiveCompaniesCount() {
        return activeCompaniesCount;
    }

    public int getActiveQueuesCount() {
        return activeQueuesCount;
    }

    public int getActivePurchasesCount() {
        return activePurchasesCount;
    }

    public int getTotalPurchasesCount() {
        return totalPurchasesCount;
    }

    public double getNewSubscriberRatePerMin() {
        return newSubscriberRatePerMin;
    }

    public double getTicketReservationRatePerMin() {
        return ticketReservationRatePerMin;
    }

    public double getTicketPurchaseRatePerMin() {
        return ticketPurchaseRatePerMin;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}