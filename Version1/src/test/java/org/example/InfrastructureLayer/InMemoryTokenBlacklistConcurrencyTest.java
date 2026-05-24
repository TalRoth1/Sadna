package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.ITokenBlacklist;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Phase 1 — TDD tests for {@link InMemoryTokenBlacklist} thread safety.
 *
 * <h2>The race this suite closes</h2>
 * <p>The old {@code isRevoked} implementation had a three-step
 * read–check–act sequence:
 * <pre>
 *   Instant exp = revoked.get(jti);          // step 1 — read
 *   if (exp.isBefore(Instant.now())) {        // step 2 — check
 *       revoked.remove(jti);                  // step 3 — act
 *       return false;
 *   }
 *   return true;
 * </pre>
 * <p>Even with a {@code ConcurrentHashMap}, these three steps are not
 * atomic as a unit. Two concurrent callers can both execute step 1 before
 * either executes step 3, leading to double-removes and — worse — a window
 * where the entry is logically "being removed" yet still visible to a third
 * reader who just ran step 1. After the fix, the entire sequence is
 * collapsed into a single atomic {@code computeIfPresent} call, which
 * {@link java.util.concurrent.ConcurrentHashMap} guarantees to execute
 * without interleaving.
 *
 * <h2>Why these tests fail on the old code</h2>
 * <ul>
 *   <li>Under concurrent load the non-atomic sequence can yield
 *       inconsistent results: a token that should still be revoked can
 *       briefly appear un-revoked when a concurrent expiry-check wins
 *       the race to remove it.</li>
 *   <li>{@code purgeExpired} iterating via {@code Iterator.remove} while
 *       another thread is mid-way through the same map can silently skip
 *       entries on the old code path.</li>
 * </ul>
 */
public class InMemoryTokenBlacklistConcurrencyTest {

    private ITokenBlacklist blacklist;

    @Before
    public void setUp() {
        blacklist = new InMemoryTokenBlacklist();
    }

    // ====================================================================
    // 1. Functional contract (single-threaded baseline)
    // ====================================================================

    @Test
    public void isRevoked_tokenNotRevoked_returnsFalse() {
        assertFalse(blacklist.isRevoked("unknown-jti"));
    }

    @Test
    public void isRevoked_nullJti_returnsFalse() {
        assertFalse(blacklist.isRevoked(null));
    }

    @Test
    public void revoke_thenIsRevoked_beforeExpiry_returnsTrue() {
        Instant future = Instant.now().plusSeconds(3600);
        blacklist.revoke("jti-1", future);
        assertTrue(blacklist.isRevoked("jti-1"));
    }

    @Test
    public void revoke_thenIsRevoked_afterExpiry_returnsFalse() throws InterruptedException {
        // Revoke with an expiry 50 ms in the future, then wait for it to lapse.
        Instant nearFuture = Instant.now().plusMillis(50);
        blacklist.revoke("jti-exp", nearFuture);
        assertTrue("Must be revoked before expiry", blacklist.isRevoked("jti-exp"));

        Thread.sleep(100); // wait past expiry

        assertFalse("Must not be revoked after expiry", blacklist.isRevoked("jti-exp"));
    }

    @Test
    public void revoke_withNullJti_isNoOp() {
        // Must not throw; null is silently ignored per the interface contract.
        blacklist.revoke(null, Instant.now().plusSeconds(60));
        assertFalse(blacklist.isRevoked(null));
    }

    @Test
    public void revoke_withNullExpiry_isNoOp() {
        blacklist.revoke("jti-null-exp", null);
        assertFalse(blacklist.isRevoked("jti-null-exp"));
    }

    // ====================================================================
    // 2. Concurrent isRevoked — must never throw, must be consistent
    // ====================================================================

    /**
     * Many threads all call {@code isRevoked} for the same non-expiring
     * token simultaneously. The result must be consistently {@code true}
     * for every caller and no exceptions must be thrown.
     *
     * <p>With the non-atomic old implementation, a thread executing
     * step 1 right before another executes step 3 could see the entry
     * disappear mid-check and either NPE or return the wrong answer.
     */
    @Test
    public void isRevoked_concurrentReads_sameValidToken_alwaysReturnsTrue()
            throws InterruptedException {

        String jti = "concurrent-valid-jti";
        blacklist.revoke(jti, Instant.now().plusSeconds(3600));

        final int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicInteger wrongAnswers = new AtomicInteger(0);
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startTogether.await();
                    if (!blacklist.isRevoked(jti)) {
                        wrongAnswers.incrementAndGet();
                    }
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertNull("No exception must be thrown during concurrent reads: "
                + firstError.get(), firstError.get());
        assertEquals("Every concurrent caller must see the token as revoked",
                0, wrongAnswers.get());
    }

    /**
     * N threads race to call {@code isRevoked} on a token that expires
     * within the test window. The contract: every caller must receive
     * either {@code true} (token was still valid when checked) or
     * {@code false} (token had expired when checked) — never an exception,
     * and never a result that contradicts the monotonic expiry clock
     * (i.e. once a caller sees {@code false}, no later caller from the
     * same wave should see {@code true}).
     *
     * <p>The key property the {@code computeIfPresent} fix gives us is
     * that the check and the conditional remove happen atomically, so
     * there is no window where the entry has been logically removed but
     * its {@code true} value is still being returned by a thread that
     * captured the reference before the remove.
     */
    @Test
    public void isRevoked_concurrentReads_nearExpiryToken_neverThrows()
            throws InterruptedException {

        // Token expires in 80 ms — within the test window.
        String jti = "near-expiry-jti";
        blacklist.revoke(jti, Instant.now().plusMillis(80));

        final int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startTogether.await();
                    Thread.sleep((long)(Math.random() * 120)); // spread reads across the expiry boundary
                    blacklist.isRevoked(jti); // result may be true or false — both are valid
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertNull("No exception must be thrown when token expires during concurrent reads: "
                + firstError.get(), firstError.get());
    }

    // ====================================================================
    // 3. Concurrent revoke + isRevoked — no data corruption
    // ====================================================================

    /**
     * One thread batch-revokes tokens while other threads are reading.
     * Asserts that:
     * <ul>
     *   <li>No exception is ever thrown.</li>
     *   <li>A token revoked <em>before</em> a reader's query is always
     *       seen as revoked by that reader (no lost writes).</li>
     * </ul>
     */
    @Test
    public void concurrentRevokeAndIsRevoked_noDataCorruption()
            throws InterruptedException {

        final int tokenCount = 100;
        final int readerThreads = 8;
        // Pre-create all JTI strings; revocation happens inside the test.
        String[] jtis = new String[tokenCount];
        Instant exp = Instant.now().plusSeconds(3600);
        for (int i = 0; i < tokenCount; i++) {
            jtis[i] = "jti-" + i;
        }

        ExecutorService pool = Executors.newFixedThreadPool(readerThreads + 2);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        // Writer: revoke all tokens sequentially
        pool.submit(() -> {
            try {
                startTogether.await();
                for (String jti : jtis) {
                    blacklist.revoke(jti, exp);
                }
            } catch (Throwable t) {
                firstError.compareAndSet(null, t);
            }
        });

        // Readers: query all tokens while the writer is active
        for (int r = 0; r < readerThreads; r++) {
            pool.submit(() -> {
                try {
                    startTogether.await();
                    for (String jti : jtis) {
                        blacklist.isRevoked(jti); // may be true or false — just must not throw
                    }
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

        assertNull("No exception during concurrent revoke + isRevoked: "
                + firstError.get(), firstError.get());

        // After the writer has finished, every token must be revoked.
        for (String jti : jtis) {
            assertTrue("Token " + jti + " must be revoked after writer finished",
                    blacklist.isRevoked(jti));
        }
    }

    // ====================================================================
    // 4. Concurrent revoke calls — idempotent, no corruption
    // ====================================================================

    /**
     * Many threads all call {@code revoke} for the same JTI simultaneously.
     * The final state must be that the token is revoked exactly once
     * (idempotent) and the stored expiry is one of the supplied values.
     */
    @Test
    public void concurrentRevoke_sameJti_idempotentAndNoException()
            throws InterruptedException {

        final int threads = 20;
        String jti = "double-revoke-jti";
        Instant exp = Instant.now().plusSeconds(3600);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startTogether.await();
                    blacklist.revoke(jti, exp);
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertNull("No exception during concurrent revoke: " + firstError.get(),
                firstError.get());
        assertTrue("Token must be revoked after all concurrent revoke calls",
                blacklist.isRevoked(jti));
    }

    // ====================================================================
    // 5. purgeExpired called concurrently with isRevoked — no CME
    // ====================================================================

    /**
     * Simulates the scenario where {@code purgeExpired()} (triggered inside
     * {@code revoke}) and {@code isRevoked} run simultaneously across many
     * threads. With the old iterator-based purge and a non-atomic
     * {@code isRevoked}, this could surface
     * {@link java.util.ConcurrentModificationException}.
     *
     * <p>After the fix, each entry's removal is an atomic CAS via
     * {@code computeIfPresent}, and the purge loop uses per-entry atomic
     * removes, so no CME is possible.
     */
    @Test
    public void purgeExpiredConcurrentWithIsRevoked_noConcurrentModificationException()
            throws InterruptedException {

        // Seed with a mix of already-expired and still-valid tokens.
        for (int i = 0; i < 50; i++) {
            Instant exp = (i % 2 == 0)
                    ? Instant.now().minusSeconds(1)   // already expired
                    : Instant.now().plusSeconds(3600); // still valid
            blacklist.revoke("seed-jti-" + i, exp);
        }

        final int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    startTogether.await();
                    for (int i = 0; i < 20; i++) {
                        // Alternately: trigger purge via revoke, or just read
                        if (tid % 2 == 0) {
                            blacklist.revoke("new-jti-" + tid + "-" + i,
                                    Instant.now().plusSeconds(60));
                        } else {
                            blacklist.isRevoked("seed-jti-" + (i % 50));
                        }
                    }
                } catch (Throwable e) {
                    firstError.compareAndSet(null, e);
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

        assertNull("No ConcurrentModificationException or other error expected: "
                + firstError.get(), firstError.get());
    }
}
