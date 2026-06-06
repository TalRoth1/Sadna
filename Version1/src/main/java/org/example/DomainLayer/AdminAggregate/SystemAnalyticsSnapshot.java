package org.example.DomainLayer.AdminAggregate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable snapshot of system-wide analytics at a single point in time.
 *
 * <p>Persisted by {@link org.example.DomainLayer.IAdminRepository} so that
 * historical snapshots can be retrieved for trend analysis.
 *
 * <p>Three new rate fields were added to support the real-time dashboard:
 * {@link #newSubscriberRatePerMin}, {@link #ticketReservationRatePerMin},
 * and {@link #ticketPurchaseRatePerMin}.  Each represents the count of that
 * event type observed inside the last 60-second sliding window, as computed
 * by {@link org.example.ApplicationLayer.ISystemMetricsTracker}.
 */
public class SystemAnalyticsSnapshot {

    private final UUID id;
    private final int registeredUsersCount;
    private final int loggedInUsersCount;
    private final int activeCompaniesCount;
    private final int activeQueuesCount;
    private final int activePurchasesCount;
    private final int totalPurchasesCount;
    // ── real-time rate fields (events per 60-second sliding window) ──────
    private final double newSubscriberRatePerMin;
    private final double ticketReservationRatePerMin;
    private final double ticketPurchaseRatePerMin;
    private final LocalDateTime createdAt;

    public SystemAnalyticsSnapshot(
            int registeredUsersCount,
            int loggedInUsersCount,
            int activeCompaniesCount,
            int activeQueuesCount,
            int activePurchasesCount,
            int totalPurchasesCount,
            double newSubscriberRatePerMin,
            double ticketReservationRatePerMin,
            double ticketPurchaseRatePerMin) {
        this.id = UUID.randomUUID();
        this.registeredUsersCount    = registeredUsersCount;
        this.loggedInUsersCount      = loggedInUsersCount;
        this.activeCompaniesCount    = activeCompaniesCount;
        this.activeQueuesCount       = activeQueuesCount;
        this.activePurchasesCount    = activePurchasesCount;
        this.totalPurchasesCount     = totalPurchasesCount;
        this.newSubscriberRatePerMin     = newSubscriberRatePerMin;
        this.ticketReservationRatePerMin = ticketReservationRatePerMin;
        this.ticketPurchaseRatePerMin    = ticketPurchaseRatePerMin;
        this.createdAt = LocalDateTime.now();
    }

    public SystemAnalyticsSnapshot(UUID id,
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

    // ── getters ──────────────────────────────────────────────────────────

    public UUID getId()                    { return id; }
    public int getRegisteredUsersCount()   { return registeredUsersCount; }
    public int getLoggedInUsersCount()     { return loggedInUsersCount; }
    public int getActiveCompaniesCount()   { return activeCompaniesCount; }
    public int getActiveQueuesCount()      { return activeQueuesCount; }
    public int getActivePurchasesCount()   { return activePurchasesCount; }
    public int getTotalPurchasesCount()    { return totalPurchasesCount; }
    public LocalDateTime getCreatedAt()    { return createdAt; }

    /** New-subscriber rate: subscriptions recorded in the last 60 seconds. */
    public double getNewSubscriberRatePerMin()      { return newSubscriberRatePerMin; }

    /** Ticket-reservation rate: reservations recorded in the last 60 seconds. */
    public double getTicketReservationRatePerMin()  { return ticketReservationRatePerMin; }

    /** Ticket-purchase rate: completed purchases recorded in the last 60 seconds. */
    public double getTicketPurchaseRatePerMin()     { return ticketPurchaseRatePerMin; }
}
