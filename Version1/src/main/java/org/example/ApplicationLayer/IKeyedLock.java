package org.example.ApplicationLayer;

import java.util.function.Supplier;

/**
 * Per-key mutual exclusion.
 *
 * <p>Two callers using <em>different</em> keys may execute concurrently;
 * two callers using the <em>same</em> key are serialized. Re-entry by the
 * same thread on the same key is permitted (ReentrantLock semantics) so
 * that a method protected by this lock can transparently call a sibling
 * method on the same aggregate without self-deadlocking.
 *
 * <p>In DDD terms this is the protector of an Aggregate's consistency
 * boundary: locking happens at the granularity of one Aggregate instance
 * (identified by {@code key}), <em>not</em> at the granularity of the
 * whole service. The natural keys for this codebase are:
 * <ul>
 *   <li>{@code String} — normalised email — for login &amp; register;</li>
 *   <li>{@code UUID} — user id — for logout and per-user mutations.</li>
 * </ul>
 *
 * <p><b>Contract.</b> Implementations must:
 * <ul>
 *   <li>be safe for concurrent use from any thread;</li>
 *   <li>release the lock even if {@code action} throws (the exception is
 *       propagated to the caller unchanged);</li>
 *   <li>reject {@code null} keys with {@link IllegalArgumentException}.</li>
 * </ul>
 *
 * <p><b>Distributed readiness.</b> The in-memory implementation supplied
 * with this interface ({@code InMemoryKeyedLock}) is suitable for a
 * single JVM. To run the service across multiple instances, supply a
 * different implementation backed by Redis (e.g. Redisson {@code RLock}),
 * by ZooKeeper, or by a database-level lock (advisory lock /
 * {@code SELECT … FOR UPDATE}). No consumer code needs to change — that
 * is the point of putting this behind an interface.
 *
 * @param <K> key type
 */
public interface IKeyedLock<K> {

    /**
     * Acquire the lock for {@code key}, run {@code action}, release the
     * lock. Any exception thrown by {@code action} is propagated unchanged
     * and the lock is still released.
     *
     * @param key    non-null key
     * @param action non-null supplier executed while holding the lock
     * @param <T>    return type of {@code action}
     * @return the value produced by {@code action}
     * @throws IllegalArgumentException if {@code key} or {@code action} is {@code null}
     */
    <T> T withLock(K key, Supplier<T> action);

    /**
     * Convenience overload for void actions. Same contract as
     * {@link #withLock(Object, Supplier)} except no value is returned.
     *
     * @throws IllegalArgumentException if {@code key} or {@code action} is {@code null}
     */
    void withLock(K key, Runnable action);
}
