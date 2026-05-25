package org.example.ApplicationLayer;

/**
 * Port (DIP) — the single contract between the Application layer and
 * whatever storage mechanism tracks real-time system metrics.
 *
 * <h2>Responsibility</h2>
 * <p>Maintains three independent, 60-second sliding-window counters and
 * exposes a per-minute event rate for each.  "Rate per minute" is defined
 * as the count of events recorded inside the most recent 60-second window
 * ending at the instant the query is made.
 *
 * <h2>Design notes</h2>
 * <ul>
 *   <li><b>Framework-agnostic</b> — no Spring or any other framework
 *       import appears here.  This interface lives in the Application
 *       layer, which is a pure-Java zone.</li>
 *   <li><b>No Instant argument on record methods</b> — time is an
 *       infrastructure concern.  The concrete adapter
 *       ({@code InMemorySystemMetricsTracker}) owns a
 *       {@link java.time.Clock} and stamps events internally, so callers
 *       never have to think about time.</li>
 *   <li><b>Implementations must be thread-safe</b> — record and query
 *       methods may be called concurrently from multiple HTTP-request
 *       threads.</li>
 * </ul>
 */
public interface ISystemMetricsTracker {

    // ── write side ───────────────────────────────────────────────────────

    /**
     * Records a new-subscriber event at the current instant.
     * Called by {@code SystemMetricsCollector} when a {@code UserRegisteredEvent} fires.
     */
    void recordSubscription();

    /**
     * Records a ticket-reservation event at the current instant.
     * Called by {@code SystemMetricsCollector} when a {@code TicketReservedEvent} fires.
     */
    void recordReservation();

    /**
     * Records a completed-purchase event at the current instant.
     * Called by {@code SystemMetricsCollector} when a {@code PurchaseCompletedEvent} fires.
     */
    void recordPurchase();

    // ── read side ────────────────────────────────────────────────────────

    /**
     * Returns the number of subscription events recorded in the last 60 seconds.
     * This equals the new-subscriber rate per minute at the current instant.
     *
     * @return a non-negative count; 0.0 when no events are in the window
     */
    double getSubscriptionRatePerMinute();

    /**
     * Returns the number of ticket-reservation events recorded in the last 60 seconds.
     *
     * @return a non-negative count; 0.0 when no events are in the window
     */
    double getReservationRatePerMinute();

    /**
     * Returns the number of completed-purchase events recorded in the last 60 seconds.
     *
     * @return a non-negative count; 0.0 when no events are in the window
     */
    double getPurchaseRatePerMinute();
}
