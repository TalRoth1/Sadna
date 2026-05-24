package org.example.InfrastructureLayer;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.example.ApplicationLayer.ILoginRateLimiter;
import org.example.ApplicationLayer.RateLimitExceededException;

/**
 * In-memory, sliding-window implementation of {@link ILoginRateLimiter}.
 *
 * <h2>Algorithm</h2>
 *
 * <p>For each account key we maintain a deque of attempt timestamps
 * (millis since epoch). On every {@link #checkAllowed(String)} call we:
 * <ol>
 *   <li>Acquire the monitor of the <em>per-account</em> window — different
 *       keys hold different monitors, so different accounts do not
 *       contend with each other;</li>
 *   <li>Purge timestamps older than {@code window};</li>
 *   <li>If the remaining count is below {@code maxAttempts}, append the
 *       current timestamp (atomically <em>reserving</em> the slot) and
 *       return normally;</li>
 *   <li>Otherwise throw {@link RateLimitExceededException}.</li>
 * </ol>
 *
 * <h2>Why {@code checkAllowed} reserves the slot</h2>
 *
 * <p>A non-reserving read-then-modify implementation is racy under
 * concurrent load: N threads can each read {@code count == 0} and all
 * pass through, defeating the limiter. Doing the read and the
 * increment inside the same monitor makes the count exact — so the
 * contract "5 attempts allowed; 50 fired concurrently" reliably yields
 * 5 passes and 45 rejections, rather than the unpredictable "somewhere
 * around 5" that a naive impl produces.
 *
 * <p>This is why {@link #recordFailure(String)} is a no-op here: the
 * slot was already counted at check time. The API still exposes
 * {@code recordFailure} so callers can state intent explicitly and so
 * a future implementation with different reservation semantics has
 * somewhere to hook.
 *
 * <h2>Thread safety</h2>
 *
 * <ul>
 *   <li>The top-level map is a {@link ConcurrentHashMap}: concurrent
 *       lookups and inserts of different keys never block each other,
 *       and {@code computeIfAbsent} guarantees that two threads racing
 *       to set up a new key receive the <em>same</em> window instance.</li>
 *   <li>Mutating a {@link FailureWindow} is only ever done while holding
 *       its own intrinsic monitor — the {@code FailureWindow} object is
 *       never published outside this class, so no other code path can
 *       see partially-modified state.</li>
 * </ul>
 *
 * <h2>Memory bound</h2>
 *
 * <p>One {@code FailureWindow} per account that has produced any
 * activity. A successful login clears the window (and removes the map
 * entry). Long-idle entries that never see a {@code recordSuccess} are
 * not actively reaped — purging happens lazily inside
 * {@link #checkAllowed(String)} when that key is next touched. For an
 * auth system this is bounded by the number of distinct accounts under
 * recent attack, which is small in practice.
 *
 * <h2>Scope</h2>
 *
 * <p>Single-JVM only. For a horizontally scaled deployment, swap in a
 * Redis-backed implementation of {@link ILoginRateLimiter}.
 */
public final class InMemoryLoginRateLimiter implements ILoginRateLimiter {

    private final int maxAttempts;
    private final long windowMillis;

    private final ConcurrentMap<String, FailureWindow> windows = new ConcurrentHashMap<>();

    /**
     * @param maxAttempts the number of attempts permitted in any single
     *                    {@code window} for a given account key (must be &gt; 0)
     * @param window      the sliding window (must be a positive duration)
     */
    public InMemoryLoginRateLimiter(int maxAttempts, Duration window) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be a positive duration");
        }
        this.maxAttempts = maxAttempts;
        this.windowMillis = window.toMillis();
    }

    @Override
    public void checkAllowed(String accountKey) {
        if (accountKey == null) {
            return; // defensive: null key is a no-op, not an error
        }
        FailureWindow w = windows.computeIfAbsent(accountKey, k -> new FailureWindow());
        synchronized (w) {
            long now = System.currentTimeMillis();
            w.purgeOlderThan(now - windowMillis);
            if (w.count() >= maxAttempts) {
                throw new RateLimitExceededException(
                        "Too many failed login attempts for this account. Please try again later.");
            }
            // Reserve the slot: counting now (inside the same monitor that
            // performed the check) is what makes the limiter exact under
            // concurrent load.
            w.add(now);
        }
    }

    @Override
    public void recordFailure(String accountKey) {
        // The slot was reserved by checkAllowed and is already counted; this
        // method is part of the public contract (so callers state outcome
        // explicitly) but is a no-op in this implementation.
    }

    @Override
    public void recordSuccess(String accountKey) {
        if (accountKey == null) {
            return;
        }
        // Successful authentication forgives prior failures: clear the
        // sliding window for this account entirely.
        windows.remove(accountKey);
    }

    /**
     * Mutable per-account state. Always accessed under its own intrinsic
     * monitor; never published outside the enclosing class.
     */
    private static final class FailureWindow {
        private final Deque<Long> timestamps = new ArrayDeque<>();

        void purgeOlderThan(long cutoffMillis) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoffMillis) {
                timestamps.pollFirst();
            }
        }

        void add(long timestampMillis) {
            timestamps.addLast(timestampMillis);
        }

        int count() {
            return timestamps.size();
        }
    }
}
