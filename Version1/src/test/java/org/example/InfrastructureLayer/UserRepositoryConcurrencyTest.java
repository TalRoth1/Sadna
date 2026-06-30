package org.example.InfrastructureLayer;

import org.example.DomainLayer.UserAggregate.User;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Phase 1 — TDD tests for {@link UserRepository} concurrency safety.
 *
 * <h2>What these tests prove</h2>
 * <ul>
 *   <li><b>No {@code ConcurrentModificationException}.</b> Concurrent reads
 *       and writes to a plain {@code HashMap} trigger CME. After migrating
 *       to {@code ConcurrentHashMap} the iteration-based helpers
 *       ({@code existsByEmail}, {@code existsByUsername}, {@code findByEmail})
 *       are safe under concurrent mutation.</li>
 *   <li><b>Email uniqueness is atomic.</b> N threads racing to register the
 *       same email must result in exactly one stored user — the check-and-add
 *       must never be torn apart by another inserting thread.</li>
 *   <li><b>Username uniqueness is atomic.</b> This is the harder race: threads
 *       with <em>different</em> emails (so the service-level email-keyed lock
 *       does not help) but the <em>same</em> username. Only an internal
 *       {@code registrationLock} inside {@code add()} can prevent both from
 *       passing the {@code existsByUsername} check and both inserting.</li>
 *   <li><b>No lost updates under a burst of distinct registrations.</b> 100
 *       threads each adding a uniquely-keyed user must all succeed — the lock
 *       must not over-serialize to the point of dropping writes.</li>
 * </ul>
 *
 * <h2>Why these tests fail on the old code</h2>
 * <ul>
 *   <li>The old {@code UserRepository} uses a plain {@code HashMap} — not
 *       thread-safe for concurrent reads and writes.</li>
 *   <li>The old {@code add(User)} has no internal locking, so the
 *       compound "check username + insert" in the service can be interleaved
 *       by a concurrent thread that has a different email key.</li>
 * </ul>
 */
public class UserRepositoryConcurrencyTest {

    private UserRepository repo;

    @Before
    public void setUp() {
        repo = new UserRepository();
    }

    // ====================================================================
    // 1. Concurrent add — same email → exactly one succeeds
    // ====================================================================

    /**
     * N threads all try to {@code add} a user with the same email but
     * distinct usernames. Because the repository's {@code add} re-checks
     * email uniqueness inside its internal lock, exactly one insert must
     * be committed and all others must be rejected with
     * {@link IllegalArgumentException}.
     *
     * <p>With the old {@code HashMap}-backed {@code add} (no internal lock),
     * this test is flaky: multiple threads can pass the existence check
     * before any one of them has finished inserting.
     */
    @Test
    public void concurrentAdd_sameEmail_exactlyOneSucceeds() throws InterruptedException {
        final int threads = 20;
        final String sharedEmail = "race@example.com";

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicInteger successCount    = new AtomicInteger(0);
        AtomicInteger duplicateCount  = new AtomicInteger(0);
        AtomicInteger unexpectedCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int tid = i;
            pool.submit(() -> {
                try {
                    startTogether.await();
                    User u = new User(UUID.randomUUID(),
                            "user-" + tid,   // unique username
                            sharedEmail,
                            "hash",
                            25f);
                    repo.add(u);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException expected) {
                    // "User email already exists." — expected for all but one
                    duplicateCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedCount.incrementAndGet();
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue("All threads must finish", pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals("Exactly one registration must succeed", 1, successCount.get());
        assertEquals("All other threads must be rejected as duplicates",
                threads - 1, duplicateCount.get());
        assertEquals("No unexpected exceptions", 0, unexpectedCount.get());
        assertTrue("The email must be present in the repository",
                repo.existsByEmail(sharedEmail));
    }

    // ====================================================================
    // 2. Concurrent add — same username, different emails → exactly one succeeds
    // ====================================================================

    /**
     * This is the subtler race: threads have <em>different</em> emails (so
     * no service-level email-keyed lock would help), but the <em>same</em>
     * username. Without a lock inside {@code add()} around the username
     * check and the put, all threads can pass {@code existsByUsername} ==
     * {@code false} before any of them has actually inserted.
     *
     * <p>After the fix, {@code add()} acquires the {@code registrationLock}
     * before the compound check, so only one thread can be in the critical
     * section at a time.
     */
    @Test
    public void concurrentAdd_sameUsername_differentEmails_exactlyOneSucceeds()
            throws InterruptedException {
        final int threads = 20;
        final String sharedUsername = "the-chosen-one";

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicInteger successCount    = new AtomicInteger(0);
        AtomicInteger duplicateCount  = new AtomicInteger(0);
        AtomicInteger unexpectedCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int tid = i;
            pool.submit(() -> {
                try {
                    startTogether.await();
                    User u = new User(UUID.randomUUID(),
                            sharedUsername,
                            "user-" + tid + "@example.com", // unique email
                            "hash",
                            25f);
                    repo.add(u);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException expected) {
                    // "Username already exists." — expected for all but one
                    duplicateCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedCount.incrementAndGet();
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue("All threads must finish", pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals("Exactly one registration must succeed", 1, successCount.get());
        assertEquals("All other threads must be rejected as duplicates",
                threads - 1, duplicateCount.get());
        assertEquals("No unexpected exceptions", 0, unexpectedCount.get());
        assertTrue("The username must be present in the repository",
                repo.existsByUsername(sharedUsername));
        assertEquals("Exactly one user must be stored", 1, repo.getAllUsers().size());
    }

    // ====================================================================
    // 3. Burst of all-distinct registrations — no lost updates
    // ====================================================================

    /**
     * 100 threads each adding a user with a fully-distinct (email, username)
     * pair. All must succeed. If the internal lock over-serializes or the
     * underlying {@code ConcurrentHashMap} drops updates, the final count
     * will be less than 100.
     */
    @Test
    public void burstOfDistinctAdds_allSucceed() throws InterruptedException {
        final int total = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Throwable> errors = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            final int tid = i;
            pool.submit(() -> {
                try {
                    startTogether.await();
                    User u = new User(UUID.randomUUID(),
                            "user-" + tid,
                            "user-" + tid + "@example.com",
                            "hash",
                            25f);
                    repo.add(u);
                } catch (Throwable t) {
                    synchronized (errors) { errors.add(t); }
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue("All threads must finish", pool.awaitTermination(20, TimeUnit.SECONDS));

        assertTrue("No errors expected; got: " + errors, errors.isEmpty());
        assertEquals("All " + total + " users must be stored",
                total, repo.getAllUsers().size());
    }

    // ====================================================================
    // 4. Concurrent read (existsByEmail) while writes are in flight
    // ====================================================================

    /**
     * Simulates the realistic scenario: one thread batch-inserts users while
     * other threads query {@code existsByEmail} / {@code existsByUsername}.
     * With a plain {@code HashMap}, the iterator inside the lookup methods
     * can throw {@link java.util.ConcurrentModificationException}. After
     * the migration to {@code ConcurrentHashMap}, this must never happen.
     */
    @Test
    public void concurrentReadsDuringWrites_noConcurrentModificationException()
            throws InterruptedException {
        final int writerCount = 4;
        final int readerCount = 4;
        final int opsPerThread = 50;
        final int totalThreads = writerCount + readerCount;

        ExecutorService pool = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        // Writers: continuously insert distinct users
        for (int w = 0; w < writerCount; w++) {
            final int wid = w;
            pool.submit(() -> {
                try {
                    startTogether.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        UUID id = UUID.randomUUID();
                        User u = new User(id,
                                "writer-" + wid + "-" + i,
                                "writer-" + wid + "-" + i + "@example.com",
                                "hash",
                                25f);
                        try { repo.add(u); } catch (IllegalArgumentException ignored) { }
                    }
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                }
            });
        }

        // Readers: continuously probe existence while writers are active
        for (int r = 0; r < readerCount; r++) {
            final int rid = r;
            pool.submit(() -> {
                try {
                    startTogether.await();
                    for (int i = 0; i < opsPerThread; i++) {
                        // These emails almost certainly don't exist yet — we
                        // just want to prove that iterating under concurrent
                        // mutation never throws CME.
                        repo.existsByEmail("probe-" + rid + "-" + i + "@example.com");
                        repo.existsByUsername("probe-" + rid + "-" + i);
                    }
                } catch (Throwable t) {
                    firstError.compareAndSet(null, t);
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue("All threads must finish", pool.awaitTermination(20, TimeUnit.SECONDS));
        assertNull("No exceptions must be thrown during concurrent read/write; got: "
                + firstError.get(), firstError.get());
    }

    // ====================================================================
    // 5. Guest add — bypasses uniqueness check, never conflicts
    // ====================================================================

    /**
     * Guest users are created with a null email and an auto-generated
     * username. The repository's {@code add} must not apply uniqueness
     * checks to guests (otherwise it would try to compare null emails,
     * which would always match and reject the second guest).
     */
    @Test
    public void addGuest_concurrently_allSucceed() throws InterruptedException {
        final int total = 50;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);

        for (int i = 0; i < total; i++) {
            pool.submit(() -> {
                try {
                    startTogether.await();
                    repo.add(new User(UUID.randomUUID())); // guest constructor
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    failCount.incrementAndGet();
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals("All " + total + " guest adds must succeed", total, successCount.get());
        assertEquals("No failures for guest adds", 0, failCount.get());
        assertEquals(total, repo.getAllUsers().size());
    }
}
