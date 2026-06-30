package org.example.ApplicationLayer;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.example.ApplicationLayer.dto.UserDTOs.LoginRequest;
import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;
import org.example.InfrastructureLayer.InMemoryLoginRateLimiter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for the new orchestrator {@code SessionService}, which sits on top
 * of {@link UserService} and {@link JwtService} and adds two new
 * cross-cutting concerns to login:
 *
 * <ol>
 *   <li><b>Single-session enforcement (kick-old).</b> When a user logs in
 *       while an older session is still active, the previous JWT's
 *       {@code jti} must be revoked via {@link ITokenBlacklist}.</li>
 *   <li><b>Per-account rate limiting.</b> Repeated failed logins for the
 *       same account get blocked with {@code RateLimitExceededException}.
 *       The throttle is keyed per-account so that a brute-force on
 *       account A does not lock out account B.</li>
 * </ol>
 *
 * <p>Distributed-readiness: every collaborator
 * ({@code IActiveSessionRegistry}, {@code ILoginRateLimiter},
 * {@link ITokenBlacklist}, {@link IKeyedLock}) is an interface, so a
 * future deployment can swap the in-memory implementation for a Redis-
 * or DB-backed one without touching {@code SessionService}.
 *
 * <p>These tests fail initially because {@code SessionService},
 * {@code IActiveSessionRegistry}, {@code ActiveSession},
 * {@code ILoginRateLimiter}, {@code RateLimitExceededException},
 * {@code InMemoryLoginRateLimiter} and {@code JwtService.MintedToken}
 * don't exist yet.
 */
public class SessionServiceTest {

    private UserService userServiceMock;
    private JwtService jwtServiceMock;
    private IActiveSessionRegistry sessionRegistryMock;
    private ITokenBlacklist tokenBlacklistMock;
    private ILoginRateLimiter rateLimiterMock;

    private SessionService sessionService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String EMAIL = "alice@example.com";
    private static final String EMAIL_NORM = "alice@example.com"; // normalised key
    private static final UserResponse USER_RESPONSE = new UserResponse(
            USER_ID, "alice", EMAIL, "LOGGED_IN", "MEMBER", 30, false);

    @Before
    public void setUp() {
        userServiceMock = mock(UserService.class);
        jwtServiceMock = mock(JwtService.class);
        sessionRegistryMock = mock(IActiveSessionRegistry.class);
        tokenBlacklistMock = mock(ITokenBlacklist.class);
        rateLimiterMock = mock(ILoginRateLimiter.class);

        sessionService = new SessionService(
                userServiceMock,
                jwtServiceMock,
                sessionRegistryMock,
                tokenBlacklistMock,
                rateLimiterMock
        );
    }

    // ================================================================
    // Single-session enforcement (kick old)
    // ================================================================

    @Test
    public void login_firstTime_noPreviousSession_doesNotRevoke() {
        Instant exp = Instant.now().plusSeconds(3600);
        JwtService.MintedToken minted = new JwtService.MintedToken("token-1", "jti-1", exp);

        when(userServiceMock.login(any(LoginRequest.class))).thenReturn(USER_RESPONSE);
        when(jwtServiceMock.mintSession(eq(USER_ID), eq("alice"), eq("MEMBER"))).thenReturn(minted);
        when(sessionRegistryMock.replaceSession(eq(USER_ID), any(ActiveSession.class)))
                .thenReturn(Optional.empty());  // no previous session

        SessionService.AuthResult result =
                sessionService.login(new LoginRequest(EMAIL, "pw"));

        assertNotNull(result);
        assertEquals("token-1", result.token());
        assertEquals("jti-1", result.jti());
        assertSame(USER_RESPONSE, result.user());

        verify(tokenBlacklistMock, never()).revoke(any(), any());
    }

    /**
     * The core "kick old session" contract: on a second login while a
     * previous session is still active, the previous {@code jti} must
     * be revoked via the blacklist.
     */
    @Test
    public void login_secondTime_revokesPreviousJti() {
        Instant exp1 = Instant.now().plusSeconds(3600);
        Instant exp2 = Instant.now().plusSeconds(3600);
        JwtService.MintedToken minted1 = new JwtService.MintedToken("token-1", "jti-1", exp1);
        JwtService.MintedToken minted2 = new JwtService.MintedToken("token-2", "jti-2", exp2);

        when(userServiceMock.login(any(LoginRequest.class))).thenReturn(USER_RESPONSE);
        when(jwtServiceMock.mintSession(eq(USER_ID), eq("alice"), eq("MEMBER")))
                .thenReturn(minted1, minted2);
        when(sessionRegistryMock.replaceSession(eq(USER_ID), any(ActiveSession.class)))
                .thenReturn(Optional.empty())                                   // first call
                .thenReturn(Optional.of(new ActiveSession("jti-1", exp1)));     // second call

        // First login: nothing to revoke
        sessionService.login(new LoginRequest(EMAIL, "pw"));
        verify(tokenBlacklistMock, never()).revoke(any(), any());

        // Second login: must revoke the previous jti
        SessionService.AuthResult second =
                sessionService.login(new LoginRequest(EMAIL, "pw"));

        verify(tokenBlacklistMock, times(1)).revoke("jti-1", exp1);
        assertEquals("token-2", second.token());
        assertEquals("jti-2", second.jti());
    }

    /**
     * The revoke + replace must be ordered so that the old session is
     * blacklisted before any caller can observe the new session as the
     * active one. (Otherwise a concurrent {@code JwtAuthFilter} might
     * accept the old token after it has been "replaced" but not yet
     * revoked.)
     *
     * <p>We assert ordering via {@link InOrder} against the mocks.
     */
    @Test
    public void login_kickOld_replaceSessionAndRevokeAreOrderedCorrectly() {
        Instant exp1 = Instant.now().plusSeconds(3600);
        Instant exp2 = Instant.now().plusSeconds(3600);
        JwtService.MintedToken minted2 = new JwtService.MintedToken("token-2", "jti-2", exp2);

        when(userServiceMock.login(any(LoginRequest.class))).thenReturn(USER_RESPONSE);
        when(jwtServiceMock.mintSession(any(), any(), any())).thenReturn(minted2);
        when(sessionRegistryMock.replaceSession(eq(USER_ID), any(ActiveSession.class)))
                .thenReturn(Optional.of(new ActiveSession("jti-1", exp1)));

        sessionService.login(new LoginRequest(EMAIL, "pw"));

        InOrder order = inOrder(sessionRegistryMock, tokenBlacklistMock);
        order.verify(sessionRegistryMock).replaceSession(eq(USER_ID), any(ActiveSession.class));
        order.verify(tokenBlacklistMock).revoke("jti-1", exp1);
    }

    // ================================================================
    // Rate limiting
    // ================================================================

    @Test
    public void login_rateLimitExceeded_throwsAndDoesNotInvokeUserService() {
        doThrow(new RateLimitExceededException("too many attempts for " + EMAIL_NORM))
                .when(rateLimiterMock).checkAllowed(EMAIL_NORM);

        assertThrows(
                RateLimitExceededException.class,
                () -> sessionService.login(new LoginRequest(EMAIL, "pw"))
        );

        // Rate limiter rejection must short-circuit BEFORE any credential check.
        verifyNoInteractions(userServiceMock);
        verifyNoInteractions(jwtServiceMock);
        verifyNoInteractions(sessionRegistryMock);
        verifyNoInteractions(tokenBlacklistMock);
    }

    @Test
    public void login_failedCredentials_recordsFailureWithRateLimiter() {
        when(userServiceMock.login(any(LoginRequest.class)))
                .thenThrow(new IllegalArgumentException("Incorrect email or password."));

        assertThrows(
                IllegalArgumentException.class,
                () -> sessionService.login(new LoginRequest(EMAIL, "wrong-pw"))
        );

        verify(rateLimiterMock, times(1)).checkAllowed(EMAIL_NORM);
        verify(rateLimiterMock, times(1)).recordFailure(EMAIL_NORM);
        verify(rateLimiterMock, never()).recordSuccess(any());
    }

    @Test
    public void login_success_resetsRateLimiter() {
        Instant exp = Instant.now().plusSeconds(3600);
        JwtService.MintedToken minted = new JwtService.MintedToken("token-1", "jti-1", exp);

        when(userServiceMock.login(any(LoginRequest.class))).thenReturn(USER_RESPONSE);
        when(jwtServiceMock.mintSession(any(), any(), any())).thenReturn(minted);
        when(sessionRegistryMock.replaceSession(eq(USER_ID), any(ActiveSession.class)))
                .thenReturn(Optional.empty());

        sessionService.login(new LoginRequest(EMAIL, "pw"));

        verify(rateLimiterMock, times(1)).checkAllowed(EMAIL_NORM);
        verify(rateLimiterMock, times(1)).recordSuccess(EMAIL_NORM);
        verify(rateLimiterMock, never()).recordFailure(any());
    }

    /**
     * Real {@link InMemoryLoginRateLimiter} under heavy concurrent load.
     *
     * <p>Spec: max 5 attempts per 60-second window per account. We fire
     * 50 concurrent failed-login attempts for the same account; exactly
     * 5 must be allowed through to {@code userService.login}, and 45
     * must be rejected with {@code RateLimitExceededException}.
     */
    @Test
    public void rateLimiter_underHeavyConcurrentLoad_countsCorrectly() throws Exception {
        ILoginRateLimiter realRateLimiter =
                new InMemoryLoginRateLimiter(5, Duration.ofSeconds(60));

        // Replace the mock with the real one for this test
        SessionService realRl = new SessionService(
                userServiceMock,
                jwtServiceMock,
                sessionRegistryMock,
                tokenBlacklistMock,
                realRateLimiter
        );

        // Every credential check fails (wrong password)
        when(userServiceMock.login(any(LoginRequest.class)))
                .thenThrow(new IllegalArgumentException("Incorrect email or password."));

        final int totalAttempts = 50;
        final int maxAllowed = 5;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startTogether = new CountDownLatch(1);
        AtomicInteger reachedUserService = new AtomicInteger(0);
        AtomicInteger rejectedByRateLimiter = new AtomicInteger(0);
        AtomicInteger otherFailures = new AtomicInteger(0);

        for (int i = 0; i < totalAttempts; i++) {
            pool.submit(() -> {
                try {
                    startTogether.await();
                    realRl.login(new LoginRequest(EMAIL, "wrong-pw"));
                } catch (RateLimitExceededException blocked) {
                    rejectedByRateLimiter.incrementAndGet();
                } catch (IllegalArgumentException badCreds) {
                    // got past the rate limiter to userService.login which threw
                    reachedUserService.incrementAndGet();
                } catch (Throwable t) {
                    otherFailures.incrementAndGet();
                }
            });
        }

        startTogether.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));

        assertEquals("Exactly " + maxAllowed + " attempts must reach userService.login",
                maxAllowed, reachedUserService.get());
        assertEquals("Remaining " + (totalAttempts - maxAllowed) + " must be rate-limited",
                totalAttempts - maxAllowed, rejectedByRateLimiter.get());
        assertEquals("No other failures", 0, otherFailures.get());
    }

    /**
     * Per-account isolation: locking out account A must not affect
     * account B. (Without per-account keying, an attacker could DoS
     * every account by flooding any single one.)
     */
    @Test
    public void rateLimiter_isPerAccountNotGlobal() throws Exception {
        ILoginRateLimiter realRateLimiter =
                new InMemoryLoginRateLimiter(3, Duration.ofSeconds(60));

        SessionService svc = new SessionService(
                userServiceMock,
                jwtServiceMock,
                sessionRegistryMock,
                tokenBlacklistMock,
                realRateLimiter
        );

        // All login calls fail credential check.
        when(userServiceMock.login(any(LoginRequest.class)))
                .thenThrow(new IllegalArgumentException("Incorrect email or password."));

        // Burn through account A's quota
        for (int i = 0; i < 5; i++) {
            try { svc.login(new LoginRequest("a@example.com", "x")); }
            catch (Exception ignored) {}
        }

        // Account A should now be locked out
        assertThrows(
                RateLimitExceededException.class,
                () -> svc.login(new LoginRequest("a@example.com", "x"))
        );

        // Account B should still be permitted to fail organically (bad creds, not rate-limited)
        for (int i = 0; i < 3; i++) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> svc.login(new LoginRequest("b@example.com", "x"))
            );
        }
    }
}
