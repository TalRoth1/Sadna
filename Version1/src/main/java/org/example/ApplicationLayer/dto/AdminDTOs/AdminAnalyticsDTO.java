package org.example.ApplicationLayer.dto.AdminDTOs;

import java.time.LocalDateTime;

/**
 * Data-transfer object for the admin analytics dashboard.
 *
 * <p>All fields are intentionally public and mutable (plain DTO style used
 * throughout this codebase).  The three {@code *RatePerMin} fields are new;
 * they carry the 60-second sliding-window event rates sourced from
 * {@link org.example.ApplicationLayer.ISystemMetricsTracker}.
 *
 * <h2>Dashboard mapping</h2>
 * <pre>
 *   loggedInUsersCount       → "Active visitors"
 *   newSubscriberRatePerMin  → "New subscribers / min"
 *   ticketReservationRatePerMin → "Ticket reservation rate / min"
 *   ticketPurchaseRatePerMin → "Ticket purchase rate / min"
 *   activeQueuesCount        → "Active queues"
 * </pre>
 */
public class AdminAnalyticsDTO {

    // ── count fields (existing) ──────────────────────────────────────────
    public int registeredUsersCount;
    /** Maps to "Active visitors" on the dashboard. */
    public int loggedInUsersCount;
    public int activeCompaniesCount;
    /** Maps to "Active queues" on the dashboard. */
    public int activeQueuesCount;
    public int activePurchasesCount;
    public int totalPurchasesCount;

    // ── rate fields (new — 60-second sliding window) ─────────────────────
    /** New-subscriber rate: registrations recorded in the last 60 seconds. */
    public double newSubscriberRatePerMin;
    /** Ticket-reservation rate: reservations recorded in the last 60 seconds. */
    public double ticketReservationRatePerMin;
    /** Ticket-purchase rate: completed purchases recorded in the last 60 seconds. */
    public double ticketPurchaseRatePerMin;

    public LocalDateTime createdAt;
}
