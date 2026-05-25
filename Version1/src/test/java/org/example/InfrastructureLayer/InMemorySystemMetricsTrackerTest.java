package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.ISystemMetricsTracker;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * TDD — RED phase.
 *
 * <h2>What is under test</h2>
 * <p>{@link InMemorySystemMetricsTracker} is the concrete Infrastructure-layer
 * adapter that implements the {@link ISystemMetricsTracker} port.  It maintains
 * three independent 60-second sliding-window counters (subscriptions,
 * reservations, purchases) and exposes a per-minute rate for each.
 *
 * <h2>Why these tests fail initially</h2>
 * <p>Neither {@code ISystemMetricsTracker} nor
 * {@code InMemorySystemMetricsTracker} exist in the codebase yet.  Every
 * test therefore fails to compile until those classes are created in Stage 3.
 *
 * <h2>Design constraint exercised here</h2>
 * <p>The implementation MUST accept a {@link java.time.Clock} via its
 * constructor so that time can be fully controlled in tests without any
 * real {@code Thread.sleep} calls.  A no-arg constructor that defaults to
 * {@code Clock.systemUTC()} is also required for production wiring in
 * {@code BeanConfig}.
 *
 * <h2>Sliding-window semantics</h2>
 * <p>An event recorded at time T is counted by a rate query issued at time Q
 * if and only if {@code Q - T < 60 seconds}.  The rate value returned is
 * the raw count of events that fall inside the window — i.e. events-per-minute
 * at the current instant.
 */
public class InMemorySystemMetricsTrackerTest {

    /**
     * A minimal, fully controllable {@link Clock} whose current time is
     * driven by {@link #advance(Duration)}.  All tests share one instance
     * created fresh in {@link #setUp()}.
     */
    private static class ControllableClock extends Clock {
        private Instant now;

        ControllableClock(Instant start) {
            this.now = start;
        }

        /** Move the clock forward by {@code duration}. */
        void advance(Duration duration) {
            this.now = now.plus(duration);
        }

        @Override public Instant instant()               { return now; }
        @Override public ZoneOffset getZone()            { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    // ── fixed reference point so tests are deterministic ──────────────────
    private static final Instant EPOCH = Instant.parse("2025-01-01T12:00:00Z");

    private ControllableClock clock;
    private ISystemMetricsTracker tracker;

    @Before
    public void setUp() {
        clock   = new ControllableClock(EPOCH);
        tracker = new InMemorySystemMetricsTracker(clock);
    }

    // ====================================================================
    // 1. Initial state — all rates must be 0
    // ====================================================================

    @Test
    public void allRates_initialState_areZero() {
        assertEquals(0.0, tracker.getSubscriptionRatePerMinute(),  0.0);
        assertEquals(0.0, tracker.getReservationRatePerMinute(),   0.0);
        assertEquals(0.0, tracker.getPurchaseRatePerMinute(),      0.0);
    }

    // ====================================================================
    // 2. Subscription rate — basic counting
    // ====================================================================

    @Test
    public void subscriptionRate_afterOneEvent_returnsOne() {
        tracker.recordSubscription();

        assertEquals(1.0, tracker.getSubscriptionRatePerMinute(), 0.0);
    }

    @Test
    public void subscriptionRate_afterMultipleEvents_returnsExactCount() {
        int count = 7;
        for (int i = 0; i < count; i++) {
            tracker.recordSubscription();
        }

        assertEquals((double) count, tracker.getSubscriptionRatePerMinute(), 0.0);
    }

    // ====================================================================
    // 3. Subscription rate — sliding-window eviction
    // ====================================================================

    @Test
    public void subscriptionRate_eventRecordedThenWindowExpires_returnsZero() {
        tracker.recordSubscription();
        // advance exactly 60 seconds — the event falls out of the [now-60s, now) window
        clock.advance(Duration.ofSeconds(60));

        assertEquals(0.0, tracker.getSubscriptionRatePerMinute(), 0.0);
    }

    @Test
    public void subscriptionRate_mixOfFreshAndStaleEvents_countsOnlyFreshOnes() {
        // record 3 events now — these will become stale
        tracker.recordSubscription();
        tracker.recordSubscription();
        tracker.recordSubscription();

        // advance 61 seconds — those 3 are now outside the window
        clock.advance(Duration.ofSeconds(61));

        // record 2 fresh events at the new "now"
        tracker.recordSubscription();
        tracker.recordSubscription();

        assertEquals(2.0, tracker.getSubscriptionRatePerMinute(), 0.0);
    }

    @Test
    public void subscriptionRate_eventAtWindowEdge_isStillCounted() {
        tracker.recordSubscription();
        // advance 59 seconds — event is still inside the [now-60s, now) window
        clock.advance(Duration.ofSeconds(59));

        assertEquals(1.0, tracker.getSubscriptionRatePerMinute(), 0.0);
    }

    // ====================================================================
    // 4. Reservation rate — basic counting and eviction
    // ====================================================================

    @Test
    public void reservationRate_afterOneEvent_returnsOne() {
        tracker.recordReservation();

        assertEquals(1.0, tracker.getReservationRatePerMinute(), 0.0);
    }

    @Test
    public void reservationRate_afterMultipleEvents_returnsExactCount() {
        int count = 5;
        for (int i = 0; i < count; i++) {
            tracker.recordReservation();
        }

        assertEquals((double) count, tracker.getReservationRatePerMinute(), 0.0);
    }

    @Test
    public void reservationRate_afterWindowExpires_returnsZero() {
        tracker.recordReservation();
        clock.advance(Duration.ofSeconds(60));

        assertEquals(0.0, tracker.getReservationRatePerMinute(), 0.0);
    }

    @Test
    public void reservationRate_mixOfFreshAndStaleEvents_countsOnlyFreshOnes() {
        tracker.recordReservation();
        tracker.recordReservation();
        clock.advance(Duration.ofSeconds(61));
        tracker.recordReservation(); // only this one is fresh

        assertEquals(1.0, tracker.getReservationRatePerMinute(), 0.0);
    }

    // ====================================================================
    // 5. Purchase rate — basic counting and eviction
    // ====================================================================

    @Test
    public void purchaseRate_afterOneEvent_returnsOne() {
        tracker.recordPurchase();

        assertEquals(1.0, tracker.getPurchaseRatePerMinute(), 0.0);
    }

    @Test
    public void purchaseRate_afterMultipleEvents_returnsExactCount() {
        int count = 4;
        for (int i = 0; i < count; i++) {
            tracker.recordPurchase();
        }

        assertEquals((double) count, tracker.getPurchaseRatePerMinute(), 0.0);
    }

    @Test
    public void purchaseRate_afterWindowExpires_returnsZero() {
        tracker.recordPurchase();
        clock.advance(Duration.ofSeconds(60));

        assertEquals(0.0, tracker.getPurchaseRatePerMinute(), 0.0);
    }

    @Test
    public void purchaseRate_mixOfFreshAndStaleEvents_countsOnlyFreshOnes() {
        tracker.recordPurchase();
        clock.advance(Duration.ofSeconds(61));
        tracker.recordPurchase();
        tracker.recordPurchase(); // 2 fresh

        assertEquals(2.0, tracker.getPurchaseRatePerMinute(), 0.0);
    }

    // ====================================================================
    // 6. Metric independence — each counter is isolated
    // ====================================================================

    @Test
    public void recordingSubscriptions_doesNotAffectReservationOrPurchaseRates() {
        tracker.recordSubscription();
        tracker.recordSubscription();
        tracker.recordSubscription();

        assertEquals(0.0, tracker.getReservationRatePerMinute(), 0.0);
        assertEquals(0.0, tracker.getPurchaseRatePerMinute(),    0.0);
    }

    @Test
    public void recordingReservations_doesNotAffectSubscriptionOrPurchaseRates() {
        tracker.recordReservation();
        tracker.recordReservation();

        assertEquals(0.0, tracker.getSubscriptionRatePerMinute(), 0.0);
        assertEquals(0.0, tracker.getPurchaseRatePerMinute(),      0.0);
    }

    @Test
    public void recordingPurchases_doesNotAffectSubscriptionOrReservationRates() {
        tracker.recordPurchase();

        assertEquals(0.0, tracker.getSubscriptionRatePerMinute(), 0.0);
        assertEquals(0.0, tracker.getReservationRatePerMinute(),  0.0);
    }

    @Test
    public void allThreeMetrics_accumulateIndependently() {
        // record different counts for each metric
        tracker.recordSubscription();
        tracker.recordSubscription();   // 2 subscriptions

        tracker.recordReservation();    // 1 reservation

        tracker.recordPurchase();
        tracker.recordPurchase();
        tracker.recordPurchase();       // 3 purchases

        assertEquals(2.0, tracker.getSubscriptionRatePerMinute(), 0.0);
        assertEquals(1.0, tracker.getReservationRatePerMinute(),  0.0);
        assertEquals(3.0, tracker.getPurchaseRatePerMinute(),     0.0);
    }

    // ====================================================================
    // 7. Lazy eviction — stale entries are purged on read, not on record
    //    (Implementation detail: the internal deque must shrink after a
    //     rate query that evicts old entries.)
    // ====================================================================

    @Test
    public void afterEviction_subsequentRecordsAccumulateCorrectly() {
        tracker.recordSubscription();        // recorded at T=0
        clock.advance(Duration.ofSeconds(61));
        // reading now evicts the old entry
        assertEquals(0.0, tracker.getSubscriptionRatePerMinute(), 0.0);

        // recording a new event at T=61 should start fresh
        tracker.recordSubscription();
        assertEquals(1.0, tracker.getSubscriptionRatePerMinute(), 0.0);
    }

    // ====================================================================
    // 8. Thread safety — concurrent recordings must not corrupt counts
    // ====================================================================

    /**
     * 50 threads each call {@code recordSubscription()} 20 times concurrently.
     * The final rate must equal exactly 1 000 — no lost updates, no
     * double-counts.  This test detects races in the underlying
     * {@code ConcurrentLinkedDeque} usage.
     */
    @Test
    public void concurrentSubscriptionRecordings_produceCorrectFinalCount()
            throws InterruptedException {

        int threads    = 50;
        int perThread  = 20;
        int expected   = threads * perThread; // 1 000

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch  ready = new CountDownLatch(threads);
        CountDownLatch  go    = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int j = 0; j < perThread; j++) {
                    tracker.recordSubscription();
                }
            });
        }

        ready.await();  // all threads ready — then release at once
        go.countDown();
        pool.shutdown();
        assertTrue("threads did not finish in time",
                pool.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals((double) expected, tracker.getSubscriptionRatePerMinute(), 0.0);
    }

    /**
     * Concurrent reads and writes must not throw {@code ConcurrentModificationException}
     * or produce negative / nonsensical rates.
     */
    @Test
    public void concurrentReadsAndWrites_neverProduceInvalidRates()
            throws InterruptedException {

        int writerThreads = 10;
        int readerThreads = 10;
        int operations    = 50;

        ExecutorService pool   = Executors.newFixedThreadPool(writerThreads + readerThreads);
        CountDownLatch  ready  = new CountDownLatch(writerThreads + readerThreads);
        CountDownLatch  go     = new CountDownLatch(1);
        List<Double>    rates  = java.util.Collections.synchronizedList(new ArrayList<>());

        // writers
        for (int i = 0; i < writerThreads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int j = 0; j < operations; j++) {
                    tracker.recordReservation();
                }
            });
        }

        // readers
        for (int i = 0; i < readerThreads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                for (int j = 0; j < operations; j++) {
                    rates.add(tracker.getReservationRatePerMinute());
                }
            });
        }

        ready.await();
        go.countDown();
        pool.shutdown();
        assertTrue("threads did not finish in time",
                pool.awaitTermination(5, TimeUnit.SECONDS));

        for (double rate : rates) {
            assertTrue("rate must be >= 0, got: " + rate, rate >= 0.0);
        }
    }
}
