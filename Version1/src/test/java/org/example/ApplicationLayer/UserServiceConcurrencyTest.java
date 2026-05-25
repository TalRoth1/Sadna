package org.example.ApplicationLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.example.ApplicationLayer.dto.UserDTOs.LoginRequest;
import org.example.ApplicationLayer.dto.UserDTOs.RegisterRequest;
import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.InfrastructureLayer.InMemoryKeyedLock;
import org.example.InfrastructureLayer.UserRepository;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Concurrency tests for {@link UserService}.
 *
 * <p>These tests pin down the behavior the {@code feature/ManyUsersLogin}
 * branch must deliver:
 * <ul>
 *   <li>Two threads logging in as <b>different</b> accounts run in parallel —
 *       no global mutex.</li>
 *   <li>Two threads logging in as the <b>same</b> account serialize
 *       safely — the {@code User.status} invariant is never observed
 *       in an inconsistent state.</li>
 *   <li>Two threads registering the <b>same email</b> at once: exactly
 *       one succeeds, exactly one fails with {@code IllegalArgumentException}.</li>
 *   <li>Two threads registering with the <b>same username, different
 *       emails</b> at once: exactly one succeeds, exactly one fails.
 *       (This is the harder race — the keyed lock alone, keyed on email,
 *       cannot protect it; the repository's compound check must be atomic.)</li>
 *   <li>A burst of {@code N} registrations with all-distinct emails &
 *       usernames must <em>all</em> succeed — the lock must not over-serialize.</li>
 * </ul>
 *
 * <p>These tests fail initially because:
 * <ul>
 *   <li>{@code IKeyedLock} and {@code InMemoryKeyedLock} don't exist.</li>
 *   <li>The {@link UserService} constructor doesn't accept an
 *       {@code IKeyedLock<String>} yet.</li>
 *   <li>The repository's compound uniqueness check isn't atomic yet.</li>
 * </ul>
 */
public class UserServiceConcurrencyTest {

    private UserRepository realRepo;       // real in-memory impl (refactored to be concurrent-safe)
    private IAuthenticationGateway gatewayMock;
    private INotifier notifierMock;
    private IKeyedLock<String> keyedLock;  // real in-memory keyed lock
    private UserService userService;

    @Before
    public void setUp() {
        realRepo = new UserRepository();
        gatewayMock = mock(IAuthenticationGateway.class);
        notifierMock = mock(INotifier.class);
        keyedLock = new InMemoryKeyedLock<>();

        // Default gateway behavior: everything valid, password hashed
        // trivially, every plaintext matches every hash. Tests that need
        // different behaviour override these.
        when(gatewayMock.verifyUserDetails(anyString(), anyString(), anyFloat(), anyString()))
                .thenReturn(true);
        when(gatewayMock.hashPassword(anyString()))
                .thenAnswer(inv -> "hash::" + inv.getArgument(0));
        when(gatewayMock.verifyPassword(anyString(), anyString()))
                .thenReturn(true);

        // EventPublisher is a no-subscriber no-op in this concurrency test context.
        userService = new UserService(realRepo, gatewayMock, notifierMock, keyedLock,
                new EventPublisher());
    }

    // ================================================================
    // Concurrent login: different accounts must run in parallel
    // ================================================================

    /**
     * If the implementation still uses a single global lock, logging in
     * as user A while user B's login is mid-flight will block. We prove
     * the opposite: while A's login is parked inside
     * {@code authGateway.verifyPassword}, B's login completes.
     */
    @Test
    public void concurrentLogins_forDifferentAccounts_runInParallel() throws Exception {
        // Two distinct registered users.
        UserResponse alice = registerHelper("alice@example.com", "alice", "Password1!");
        UserResponse bob   = registerHelper("bob@example.com",   "bob",   "Password1!");

        CountDownLatch aliceVerifyEntered = new CountDownLatch(1);
        CountDownLatch aliceVerifyMayReturn = new CountDownLatch(1);

        // Park alice's verifyPassword call. bob's verifyPassword returns true immediately.
        when(gatewayMock.verifyPassword(eq("alice-pw"), anyString())).thenAnswer(inv -> {
            aliceVerifyEntered.countDown();
            aliceVerifyMayReturn.await(5, TimeUnit.SECONDS);
            return true;
        });
        when(gatewayMock.verifyPassword(eq("bob-pw"), anyString())).thenReturn(true);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> bobError = new AtomicReference<>();
        AtomicBoolean bobCompletedWhileAliceParked = new AtomicBoolean(false);

        pool.submit(() -> {
            LoginRequest req = new LoginRequest("alice@example.com", "alice-pw");
            userService.login(req);
        });

        // Wait until alice is parked inside verifyPassword.
        assertTrue(
                "Alice's login must reach verifyPassword before we start Bob",
                aliceVerifyEntered.await(3, TimeUnit.SECONDS)
        );

        pool.submit(() -> {
            try {
                LoginRequest req = new LoginRequest("bob@example.com", "bob-pw");
                UserResponse result = userService.login(req);
                // If we got here while alice is still parked, parallelism works.
                if (aliceVerifyMayReturn.getCount() == 1) {
                    bobCompletedWhileAliceParked.set(true);
                }
                assertEquals(bob.userId, result.userId);
            } catch (Throwable t) {
                bobError.set(t);
            }
        });

        // Bob should finish quickly even though Alice is parked.
        pool.shutdown();
        // Don't wait for full termination yet — Alice is still parked.
        // But Bob's submission has been queued; give it room.
        Thread.sleep(500);

        assertTrue(
                "Bob's login should have completed while Alice is parked. " +
                "If this fails, UserService is still using a global lock.",
                bobCompletedWhileAliceParked.get()
        );

        // Release Alice and clean up.
        aliceVerifyMayReturn.countDown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));

        if (bobError.get() != null) {
            throw new AssertionError("Bob's login threw unexpectedly", bobError.get());
        }
        // Sanity: alice did exist.
        assertNotEquals(alice.userId, bob.userId);
    }

    // ================================================================
    // Concurrent login: same account must serialize
    // ================================================================

    /**
     * Two threads logging in as the same account must serialize: the
     * {@code User.status} invariant must never be observed inconsistently
     * (e.g. a half-applied transition), and the resulting state is
     * {@code LOGGED_IN} exactly once.
     */
    @Test
    public void concurrentLogins_forSameAccount_serializeSafely() throws Exception {
        UserResponse user = registerHelper("eve@example.com", "eve", "Password1!");
        // After register, eve is LOGGED_IN. Force her back to NOT_LOGGED_IN
        // so the test starts from a clean state.
        userService.logout(user.userId);

        final int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Counter that increments on entry and decrements on exit to the
        // *real* critical section (around the user-status transition).
        // The keyed lock is meant to guarantee this never exceeds 1.
        AtomicInteger insideCriticalSection = new AtomicInteger(0);
        AtomicInteger maxInside = new AtomicInteger(0);

        // We hook the gateway's verifyPassword to observe entry/exit of
        // the locked region — that's the closest signal we have without
        // changing production code for the test.
        when(gatewayMock.verifyPassword(anyString(), anyString())).thenAnswer(inv -> {
            int now = insideCriticalSection.incrementAndGet();
            maxInside.updateAndGet(prev -> Math.max(prev, now));
            try {
                Thread.sleep(1);
            } finally {
                insideCriticalSection.decrementAndGet();
            }
            return true;
        });

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startTogether.await();
                    userService.login(new LoginRequest("eve@example.com", "pw"));
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    failureCount.incrementAndGet();
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        // Property: at no point did two threads share the critical section.
        assertEquals(
                "Same-account logins must serialize; saw " + maxInside.get() + " concurrent occupants",
                1, maxInside.get()
        );

        // All 16 calls should have succeeded — login is idempotent on
        // already-LOGGED_IN per the existing UserService contract.
        assertEquals(threads, successCount.get());
        assertEquals(0, failureCount.get());
    }

    // ================================================================
    // Concurrent register: same email — exactly one wins
    // ================================================================

    @Test
    public void concurrentRegister_sameEmail_exactlyOneSucceeds() throws Exception {
        final int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateRejectCount = new AtomicInteger(0);
        AtomicInteger unexpectedFailures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int tid = i;
            pool.submit(() -> {
                try {
                    startTogether.await();
                    RegisterRequest req = new RegisterRequest(
                            "user-" + tid,           // distinct username per thread
                            "shared@example.com",    // same email
                            "Password1!",
                            25
                    );
                    userService.register(req);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException expected) {
                    duplicateRejectCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedFailures.incrementAndGet();
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals("Exactly one registration should succeed", 1, successCount.get());
        assertEquals("All other " + (threads - 1) + " must be rejected as duplicates",
                threads - 1, duplicateRejectCount.get());
        assertEquals("No unexpected exceptions", 0, unexpectedFailures.get());
        assertTrue("Repository must contain exactly one user with the shared email",
                realRepo.existsByEmail("shared@example.com"));
    }

    // ================================================================
    // Concurrent register: same USERNAME, different emails — exactly one wins
    // ================================================================

    /**
     * This is the test that catches the layer-2 race I identified in the
     * design: keyed-on-email locks let these two threads in parallel,
     * so the {@code existsByUsername + add} pair in the repository must
     * be atomic on its own. Without that atomicity, both threads pass
     * the existence check and both insert.
     */
    @Test
    public void concurrentRegister_sameUsernameDifferentEmails_exactlyOneSucceeds() throws Exception {
        final int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateRejectCount = new AtomicInteger(0);
        AtomicInteger unexpectedFailures = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int tid = i;
            pool.submit(() -> {
                try {
                    startTogether.await();
                    RegisterRequest req = new RegisterRequest(
                            "shared-username",            // same username
                            "user-" + tid + "@example.com", // distinct email
                            "Password1!",
                            25
                    );
                    userService.register(req);
                    successCount.incrementAndGet();
                } catch (IllegalArgumentException expected) {
                    duplicateRejectCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedFailures.incrementAndGet();
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals("Exactly one registration with the shared username should succeed",
                1, successCount.get());
        assertEquals("All other " + (threads - 1) + " must be rejected as duplicates",
                threads - 1, duplicateRejectCount.get());
        assertEquals("No unexpected exceptions", 0, unexpectedFailures.get());
        assertTrue(realRepo.existsByUsername("shared-username"));
    }

    // ================================================================
    // Burst of all-distinct registrations: no over-serialization
    // ================================================================

    /**
     * Sanity check: if the keyed lock works, N distinct (email, username)
     * pairs can all register concurrently and all succeed. If the lock
     * is too coarse, this still passes but slowly; if the repo isn't
     * concurrent-safe, this can fail with lost updates.
     */
    @Test
    public void burstOfDistinctRegistrations_allSucceed() throws Exception {
        final int total = 100;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Throwable> errors = new ArrayList<>();

        for (int i = 0; i < total; i++) {
            final int tid = i;
            pool.submit(() -> {
                try {
                    startTogether.await();
                    RegisterRequest req = new RegisterRequest(
                            "user-" + tid,
                            "user-" + tid + "@example.com",
                            "Password1!",
                            25
                    );
                    userService.register(req);
                } catch (Throwable t) {
                    synchronized (errors) {
                        errors.add(t);
                    }
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(20, TimeUnit.SECONDS));

        assertTrue("All " + total + " distinct registrations must succeed. " +
                        "Failures: " + errors,
                errors.isEmpty());
        assertEquals("Repository must contain " + total + " users",
                total, realRepo.getAllUsers().size());
    }

    // ================================================================
    // Helpers
    // ================================================================

    private UserResponse registerHelper(String email, String username, String password) {
        RegisterRequest req = new RegisterRequest(username, email, password, 25);
        return userService.register(req);
    }
}
