package org.example.ApplicationLayer;

/**
 * Per-account rate limiter for login attempts.
 *
 * <p>Defends against brute-force attacks by capping the number of failed
 * attempts that may occur within a sliding window for a single account
 * key. Locking out account A must <em>not</em> affect any other account
 * B — otherwise an attacker could DoS any account by flooding their own.
 *
 * <h2>Usage pattern</h2>
 *
 * <p>The orchestrating service ({@code SessionService}) calls the limiter
 * around each login attempt:
 *
 * <pre>{@code
 * rateLimiter.checkAllowed(accountKey);            // may throw RateLimitExceededException
 * try {
 *     UserResponse user = userService.login(request);
 *     rateLimiter.recordSuccess(accountKey);       // clear prior failures
 *     // … mint token, register session, return result
 * } catch (Exception authFailure) {
 *     rateLimiter.recordFailure(accountKey);       // confirm the failure
 *     throw authFailure;
 * }
 * }</pre>
 *
 * <h2>Atomicity contract</h2>
 *
 * <p>Implementations <em>must</em> guarantee that concurrent calls to
 * {@link #checkAllowed(String)} for the same account key never
 * collectively grant more than the configured maximum number of permits
 * within a single window. A non-atomic read-then-modify implementation
 * is incorrect: under flood-style load, N threads can each read
 * {@code count == 0} and all pass through. Implementations must close
 * that race internally.
 *
 * <h2>Distributed readiness</h2>
 *
 * <p>The in-memory implementation supplied with this interface
 * ({@code InMemoryLoginRateLimiter}) is suitable for a single JVM. For a
 * multi-instance deployment, supply a Redis-backed implementation (e.g.
 * {@code INCR + EXPIRE}, or a token-bucket Lua script) of this interface.
 * {@code SessionService} does not need to change.
 */
public interface ILoginRateLimiter {

    /**
     * Atomically check whether one more attempt is allowed for
     * {@code accountKey}.
     *
     * <p>If the configured limit has been exhausted within the current
     * window, throws {@link RateLimitExceededException}. Otherwise
     * returns normally. Whether the call also <em>reserves</em> the slot
     * (so that a subsequent concurrent {@code checkAllowed} would see
     * the updated count) is an implementation detail — see the
     * implementing class's javadoc.
     *
     * @param accountKey stable per-account key (e.g. normalised email).
     *                   A {@code null} key is treated as a no-op.
     * @throws RateLimitExceededException if too many recent failures
     */
    void checkAllowed(String accountKey);

    /**
     * Notifies the limiter that the attempt previously cleared by
     * {@link #checkAllowed(String)} has failed (wrong password, user
     * not found, …). Implementations that already counted the attempt
     * inside {@link #checkAllowed(String)} may treat this as a no-op;
     * the call is still part of the public contract so that callers
     * state the outcome explicitly.
     *
     * @param accountKey same key passed to the preceding
     *                   {@link #checkAllowed(String)} call; {@code null} is a no-op.
     */
    void recordFailure(String accountKey);

    /**
     * Notifies the limiter that the attempt succeeded. Implementations
     * typically clear the per-account window so that prior failures do
     * not penalise an already-successful authentication.
     *
     * @param accountKey same key passed to the preceding
     *                   {@link #checkAllowed(String)} call; {@code null} is a no-op.
     */
    void recordSuccess(String accountKey);
}
