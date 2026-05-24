package org.example.InfrastructureLayer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.ApplicationLayer.IKeyedLock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * Contract tests for {@link IKeyedLock} using the in-memory implementation.
 *
 * <p>This is the foundation of the concurrent-login feature. Every test here
 * proves a property the rest of the system relies on:
 * <ul>
 *   <li><b>Different keys → parallel.</b> If this fails, "many users login
 *       simultaneously" still bottlenecks on a global mutex.</li>
 *   <li><b>Same key → serialized.</b> If this fails, two threads racing on
 *       the same account would corrupt the User aggregate's status.</li>
 *   <li><b>Reentrant.</b> A thread already holding key K can call back into
 *       UserService for the same K without self-deadlocking.</li>
 *   <li><b>Lock released on exception.</b> A throwing action must not leak
 *       the lock; the next acquirer must be able to proceed.</li>
 * </ul>
 *
 * <p>These tests fail initially because neither {@code IKeyedLock} nor
 * {@code InMemoryKeyedLock} exist yet.
 */
public class InMemoryKeyedLockTest {

    private IKeyedLock<String> keyedLock;

    @Before
    public void setUp() {
        keyedLock = new InMemoryKeyedLock<>();
    }

    // ================================================================
    // Functional contract
    // ================================================================

    @Test
    public void withLock_supplierVariant_returnsActionResult() {
        String result = keyedLock.withLock("alice@example.com", () -> "hello");
        assertEquals("hello", result);
    }

    @Test
    public void withLock_runnableVariant_executesAction() {
        AtomicBoolean ran = new AtomicBoolean(false);
        keyedLock.withLock("alice@example.com", () -> ran.set(true));
        assertTrue(ran.get());
    }

    @Test
    public void withLock_nullKey_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> keyedLock.withLock((String) null, () -> "x")
        );
    }

    // ================================================================
    // Concurrency: different keys must NOT block each other
    // ================================================================

    /**
     * Key invariant: two threads holding two <em>different</em> keys must
     * be able to make progress at the same time. If they don't, the lock
     * is effectively global and the whole concurrent-login feature is
     * meaningless.
     *
     * <p>We hold key "A" inside thread 1 (blocked on a latch) and try to
     * acquire key "B" from thread 2. Thread 2 must finish while thread 1
     * is still parked.
     */
    @Test
    public void withLock_differentKeys_runInParallel() throws InterruptedException {
        CountDownLatch threadAEntered = new CountDownLatch(1);
        CountDownLatch threadAMayRelease = new CountDownLatch(1);
        AtomicBoolean threadBCompletedWhileAHeld = new AtomicBoolean(false);

        Thread threadA = new Thread(() -> keyedLock.withLock("A", () -> {
            threadAEntered.countDown();
            try {
                threadAMayRelease.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        Thread threadB = new Thread(() -> keyedLock.withLock("B", () -> {
            // If different-key parallelism works, A is still parked while
            // we get here. If it doesn't, we're blocked until A releases.
            if (threadAEntered.getCount() == 0 && threadAMayRelease.getCount() == 1) {
                threadBCompletedWhileAHeld.set(true);
            }
        }));

        threadA.start();
        // Make sure A has the lock before we even start B.
        assertTrue(threadAEntered.await(2, TimeUnit.SECONDS));

        threadB.start();
        threadB.join(2_000);

        assertFalse("Thread B should not still be running once A has the lock", threadB.isAlive());
        assertTrue(
                "Thread B must complete its critical section while Thread A still holds key A",
                threadBCompletedWhileAHeld.get()
        );

        // Cleanup: let A finish.
        threadAMayRelease.countDown();
        threadA.join(2_000);
    }

    // ================================================================
    // Concurrency: same key MUST block
    // ================================================================

    /**
     * Two threads racing on the same key must serialize: at any instant
     * at most one of them is inside the critical section. We assert this
     * by tracking the number of concurrent occupants — it must never
     * exceed 1 across many iterations.
     */
    @Test
    public void withLock_sameKey_serializesAcrossThreads() throws InterruptedException {
        final int iterations = 200;
        final int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger occupants = new AtomicInteger(0);
        AtomicInteger maxOccupants = new AtomicInteger(0);
        AtomicInteger violations = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(threads * iterations);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < iterations; i++) {
                    keyedLock.withLock("contended-key", () -> {
                        int now = occupants.incrementAndGet();
                        maxOccupants.updateAndGet(prev -> Math.max(prev, now));
                        if (now > 1) {
                            violations.incrementAndGet();
                        }
                        // Tiny delay widens the race window; with a correct
                        // lock the assertion above still holds.
                        try {
                            Thread.sleep(0, 1_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        occupants.decrementAndGet();
                    });
                    done.countDown();
                }
            });
        }

        assertTrue("All tasks must finish within timeout", done.await(20, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals("Lock must never have more than one occupant", 1, maxOccupants.get());
        assertEquals("No mutual-exclusion violations should have been observed", 0, violations.get());
    }

    // ================================================================
    // Reentrancy: same thread re-acquiring same key must NOT deadlock
    // ================================================================

    /**
     * The same thread holding key K must be able to re-enter the lock
     * for K without deadlocking. This is essential because a public
     * UserService method may call another public UserService method
     * (e.g. register → internal validation → login bookkeeping) and we
     * don't want library users to have to reason about which method
     * holds which lock.
     */
    @Test(timeout = 3_000)
    public void withLock_sameThreadReentersSameKey_doesNotDeadlock() {
        String result = keyedLock.withLock("K", () ->
                keyedLock.withLock("K", () -> "inner")
        );
        assertEquals("inner", result);
    }

    // ================================================================
    // Exception safety: lock released even if action throws
    // ================================================================

    @Test
    public void withLock_actionThrows_lockIsStillReleased() {
        try {
            keyedLock.withLock("K", () -> {
                throw new RuntimeException("boom");
            });
        } catch (RuntimeException expected) {
            // expected; we're checking that the lock didn't leak
        }

        // If the lock leaked, this call would hang. We assert it returns
        // promptly by enforcing a short timeout via a dedicated thread.
        AtomicBoolean acquired = new AtomicBoolean(false);
        Thread t = new Thread(() -> keyedLock.withLock("K", () -> acquired.set(true)));
        t.start();
        try {
            t.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertFalse("Second acquirer thread should have completed (lock not leaked)", t.isAlive());
        assertTrue("Second acquirer should have entered the critical section", acquired.get());
    }
}
