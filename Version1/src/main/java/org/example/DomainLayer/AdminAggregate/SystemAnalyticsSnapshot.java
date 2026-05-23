package org.example.DomainLayer.AdminAggregate;

import java.time.LocalDateTime;
import java.util.UUID;

public class SystemAnalyticsSnapshot {
    private final UUID id;
    private final int registeredUsersCount;
    private final int loggedInUsersCount;
    private final int activeCompaniesCount;
    private final int activeQueuesCount;
    private final int activePurchasesCount;
    private final int totalPurchasesCount;
    private final LocalDateTime createdAt;

    public SystemAnalyticsSnapshot(
            int registeredUsersCount,
            int loggedInUsersCount,
            int activeCompaniesCount,
            int activeQueuesCount,
            int activePurchasesCount,
            int totalPurchasesCount) {
        this.id = UUID.randomUUID();
        this.registeredUsersCount = registeredUsersCount;
        this.loggedInUsersCount = loggedInUsersCount;
        this.activeCompaniesCount = activeCompaniesCount;
        this.activeQueuesCount = activeQueuesCount;
        this.activePurchasesCount = activePurchasesCount;
        this.totalPurchasesCount = totalPurchasesCount;
        this.createdAt = LocalDateTime.now();
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}