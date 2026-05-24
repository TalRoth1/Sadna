package org.example.InfrastructureLayer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.example.ApplicationLayer.IKeyedLock;

/**
 * In-memory implementation of {@link IKeyedLock}: one {@link ReentrantLock}
 * per distinct key, stored in a {@link ConcurrentHashMap}.
 *
 * <h2>How thread safety is achieved</h2>
 *
 * <p>The lookup "give me the lock for key K" is itself atomic via
 * {@link ConcurrentMap#computeIfAbsent}. Two threads asking for the same
 * key receive the <em>same</em> {@code ReentrantLock} instance and
 * therefore serialize on the same monitor. Two threads asking for
 * different keys receive different locks and proceed in parallel.
 *
 * <p>{@link ReentrantLock} guarantees that:
 * <ul>
 *   <li>a thread already holding the lock may acquire it again
 *       (no self-deadlock on re-entry);</li>
 *   <li>each {@code lock()} must be balanced by exactly one
 *       {@code unlock()} — which we guarantee with the {@code try/finally}
 *       below, so the lock is released even when {@code action} throws.</li>
 * </ul>
 *
 * <h2>Memory characteristics</h2>
 *
 * <p>Locks are retained for the lifetime of the process — one entry per
 * key ever seen. For an authentication system this is bounded by the
 * number of distinct user identifiers in active use, which is well
 * within the JVM's comfort zone for any realistic deployment. If
 * unbounded growth ever becomes a concern (e.g. adversarial usage
 * cycling through millions of keys), this class can be swapped for a
 * reference-counted or fixed-stripe variant without changing a single
 * line of consumer code — that is the point of the {@link IKeyedLock}
 * interface.
 *
 * <h2>Scope</h2>
 *
 * <p>Single-JVM only. For a horizontally scaled deployment, replace this
 * bean with a distributed-lock implementation of {@link IKeyedLock}
 * (Redis, ZooKeeper, advisory DB lock, etc.).
 */
public final class InMemoryKeyedLock<K> implements IKeyedLock<K> {

    private final ConcurrentMap<K, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public <T> T withLock(K key, Supplier<T> action) {
        requireNonNull(key, "key");
        requireNonNull(action, "action");
        ReentrantLock lock = lockFor(key);
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void withLock(K key, Runnable action) {
        requireNonNull(key, "key");
        requireNonNull(action, "action");
        ReentrantLock lock = lockFor(key);
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the canonical lock for {@code key}, creating it on first
     * use. Concurrent callers asking for the same key are guaranteed to
     * receive the same {@code ReentrantLock} instance because
     * {@code computeIfAbsent} applies its factory at most once per absent
     * key.
     */
    private ReentrantLock lockFor(K key) {
        return locks.computeIfAbsent(key, k -> new ReentrantLock());
    }

    private static void requireNonNull(Object o, String name) {
        if (o == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
