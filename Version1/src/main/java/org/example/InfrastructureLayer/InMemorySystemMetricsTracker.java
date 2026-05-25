package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.ISystemMetricsTracker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Adapter — in-process, thread-safe implementation of {@link ISystemMetricsTracker}.
 *
 * <h2>Sliding-window algorithm</h2>
 * <p>Each metric is backed by a {@link ConcurrentLinkedDeque}{@code <Instant>}.
 * Recording an event appends the current clock instant to the deque's tail
 * (O(1)).  Querying a rate first evicts all entries whose age exceeds
 * {@value #WINDOW_SECONDS} seconds (lazy purge via {@code removeIf}), then
 * returns the deque's size — which equals the event count in the last minute,
 * i.e. the events-per-minute rate.
 *
 * <h2>Thread safety</h2>
 * <p>{@link ConcurrentLinkedDeque} is fully thread-safe for concurrent
 * {@code addLast}/{@code removeIf}/{@code size} operations.  No external
 * synchronisation is required.  Reads and writes may interleave; the worst
 * case is a slightly stale count (one in-flight {@code addLast} beats the
 * {@code removeIf} or vice-versa), which is entirely acceptable for a
 * real-time monitoring dashboard.
 *
 * <h2>Clock injection (testability)</h2>
 * <p>Production code uses the no-arg constructor (defaults to
 * {@link Clock#systemUTC()}).  Tests inject a {@code ControllableClock}
 * to advance time deterministically without {@code Thread.sleep}.
 *
 * <h2>Clean-architecture compliance</h2>
 * <p>This class belongs to the Infrastructure layer and intentionally has
 * no Spring annotations.  It is exposed as a {@code @Bean} in
 * {@code BeanConfig} so that Spring can inject it wherever
 * {@link ISystemMetricsTracker} is declared as a constructor parameter.
 */
public class InMemorySystemMetricsTracker implements ISystemMetricsTracker {

    static final int WINDOW_SECONDS = 60;
    private static final Duration WINDOW = Duration.ofSeconds(WINDOW_SECONDS);

    private final Clock clock;

    private final ConcurrentLinkedDeque<Instant> subscriptions = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Instant> reservations  = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Instant> purchases     = new ConcurrentLinkedDeque<>();

    // ── constructors ─────────────────────────────────────────────────────

    /**
     * Production constructor — uses the system UTC clock.
     */
    public InMemorySystemMetricsTracker() {
        this(Clock.systemUTC());
    }

    /**
     * Test constructor — accepts any {@link Clock} so that unit tests can
     * control the passage of time without real {@code Thread.sleep} calls.
     *
     * @param clock the clock to use for timestamping events and evaluating
     *              the sliding window boundary; must not be {@code null}
     */
    public InMemorySystemMetricsTracker(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("Clock must not be null");
        }
        this.clock = clock;
    }

    // ── write side ───────────────────────────────────────────────────────

    @Override
    public void recordSubscription() {
        subscriptions.addLast(clock.instant());
    }

    @Override
    public void recordReservation() {
        reservations.addLast(clock.instant());
    }

    @Override
    public void recordPurchase() {
        purchases.addLast(clock.instant());
    }

    // ── read side ────────────────────────────────────────────────────────

    @Override
    public double getSubscriptionRatePerMinute() {
        return countWithinWindow(subscriptions);
    }

    @Override
    public double getReservationRatePerMinute() {
        return countWithinWindow(reservations);
    }

    @Override
    public double getPurchaseRatePerMinute() {
        return countWithinWindow(purchases);
    }

    // ── private helpers ──────────────────────────────────────────────────

    /**
     * Evicts stale entries from {@code deque} (those older than
     * {@link #WINDOW}) and returns the number of entries that remain.
     *
     * <p>The eviction predicate uses strict inequality: an event recorded
     * exactly {@code WINDOW_SECONDS} seconds ago is considered outside the
     * window ({@code !isAfter(threshold)} → evicted), while an event
     * recorded one nanosecond less than that is still inside
     * ({@code isAfter(threshold)} → retained).  This matches the test
     * assertions: "advance 60 s → rate = 0", "advance 59 s → rate = 1".
     *
     * @param deque the metric deque to operate on
     * @return count of events inside the window, as a {@code double} for
     *         direct use in the DTO's rate fields
     */
    private double countWithinWindow(ConcurrentLinkedDeque<Instant> deque) {
        Instant threshold = clock.instant().minus(WINDOW);
        deque.removeIf(t -> !t.isAfter(threshold));
        return (double) deque.size();
    }
}
