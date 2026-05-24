package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.ITokenBlacklist;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory implementation of {@link ITokenBlacklist}.
 *
 * <h2>Phase 1 — Blacklist Race Condition Fix</h2>
 *
 * <p>The previous implementation of {@link #isRevoked(String)} contained a
 * classic <em>check-then-act</em> race:
 *
 * <pre>
 *   Instant exp = revoked.get(jti);        // step 1 — read
 *   if (exp.isBefore(Instant.now())) {      // step 2 — check expiry
 *       revoked.remove(jti);               // step 3 — conditional remove
 *       return false;
 *   }
 *   return true;
 * </pre>
 *
 * <p>Even though {@link ConcurrentHashMap} makes each individual operation
 * atomic, the three steps above are <em>not</em> atomic as a unit. A thread
 * executing step 1 can be preempted before step 3, allowing a second
 * concurrent thread to see the same (still-present) entry, reach a different
 * conclusion about its expiry, and leave the map in an inconsistent state.
 * Under heavy concurrent load (e.g. {@code PurgeExpiredConcurrentWithIsRevoked})
 * this manifested as incorrect {@code true}/{@code false} results and
 * intermittent {@link java.util.ConcurrentModificationException}s from the
 * old iterator-based {@code purgeExpired}.
 *
 * <h2>Fix: collapse to a single atomic {@code computeIfPresent}</h2>
 *
 * <p>{@link ConcurrentHashMap#computeIfPresent} executes its remapping
 * function <em>atomically with respect to other operations on the same
 * key</em> (per the Java SE specification). By placing both the expiry
 * check and the conditional removal inside the function, we guarantee that
 * no other thread can interleave between the read and the remove:
 *
 * <pre>
 *   AtomicBoolean result = new AtomicBoolean(false);
 *   revoked.computeIfPresent(jti, (k, exp) -&gt; {
 *       if (exp.isBefore(Instant.now())) {
 *           return null;          // return null ⟹ remove the entry
 *       }
 *       result.set(true);         // genuinely revoked
 *       return exp;               // keep the entry
 *   });
 *   return result.get();
 * </pre>
 *
 * <h2>Lazy expiry eviction</h2>
 *
 * <p>Expired entries are evicted lazily: {@link #isRevoked(String)} removes
 * an entry the moment it is observed to have expired, and {@link #revoke}
 * triggers a full sweep via {@link #purgeExpired()} so the map does not
 * grow without bound. The sweep uses per-entry atomic removes
 * ({@code remove(key, value)}) rather than an iterator with side effects,
 * avoiding any possibility of CME.
 *
 * <h2>Scope</h2>
 * <p>Single-JVM only. For a clustered deployment, back this with a shared
 * cache (Redis {@code SET jti EX <ttl>} + {@code EXISTS jti}) and provide a
 * new implementation of {@link ITokenBlacklist}. No consumer code changes.
 */
public class InMemoryTokenBlacklist implements ITokenBlacklist {

    /**
     * Map from JWT id ({@code jti}) to its original expiry instant.
     *
     * <p>{@code ConcurrentHashMap} provides safe concurrent reads and writes
     * at the individual-operation level. All multi-step sequences in this
     * class are further collapsed into single atomic operations
     * ({@code computeIfPresent}, conditional {@code remove}).
     */
    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------
    // ITokenBlacklist
    // ------------------------------------------------------------------

    /**
     * Marks {@code jti} as revoked until {@code expiresAt}.
     *
     * <p>Silently ignores null or blank {@code jti} and null
     * {@code expiresAt} — these represent malformed inputs that the JWT
     * layer should have rejected before reaching here.
     *
     * <p>Triggers {@link #purgeExpired()} opportunistically so the map
     * size stays bounded by the number of un-expired revoked tokens rather
     * than the total number of revocations ever issued.
     *
     * @param jti       the JWT id claim; null/blank is a no-op
     * @param expiresAt the token's original expiry; null is a no-op
     */
    @Override
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }
        revoked.put(jti, expiresAt);
        purgeExpired(); // opportunistic bounded-size maintenance
    }

    /**
     * Returns {@code true} if and only if {@code jti} has been explicitly
     * revoked <em>and</em> its revocation has not yet expired.
     *
     * <p>The check and the conditional removal are performed inside a single
     * {@link ConcurrentHashMap#computeIfPresent} call, which is atomic
     * per the Java SE specification. There is therefore no window between
     * "reading the expiry" and "removing the expired entry" that another
     * thread could exploit to observe an inconsistent intermediate state.
     *
     * <p>Expired entries are removed as a side-effect of this call (lazy
     * eviction), so the map self-prunes over time without a background
     * thread.
     *
     * @param jti the JWT id claim to check; null always returns {@code false}
     * @return {@code true} if the token is currently revoked and not expired
     */
    @Override
    public boolean isRevoked(String jti) {
        if (jti == null) return false;

        // AtomicBoolean is used here purely as a mutable boolean carrier
        // that is accessible from inside the lambda. The lambda itself is
        // executed under ConcurrentHashMap's per-key lock, so the AtomicBoolean
        // is only ever written by one thread at a time for a given jti.
        AtomicBoolean isCurrentlyRevoked = new AtomicBoolean(false);

        revoked.computeIfPresent(jti, (key, expiresAt) -> {
            if (expiresAt.isBefore(Instant.now())) {
                // Return null to atomically remove the expired entry.
                return null;
            }
            // Entry is still valid — record the result and keep the entry.
            isCurrentlyRevoked.set(true);
            return expiresAt;
        });

        return isCurrentlyRevoked.get();
    }

    // ------------------------------------------------------------------
    // Private maintenance
    // ------------------------------------------------------------------

    /**
     * Sweeps the map and atomically removes all entries whose expiry has
     * passed.
     *
     * <p>Each removal uses
     * {@link ConcurrentHashMap#remove(Object, Object)} — the conditional
     * form that only removes the entry if the value still matches. This
     * guarantees that a concurrent {@link #revoke} call that has re-inserted
     * a new expiry for the same {@code jti} (unusual but theoretically
     * possible) is not silently overwritten.
     *
     * <p>This method is intentionally <em>not</em> synchronised as a whole:
     * it only needs to make a best-effort sweep. Entries that slip through
     * because of a concurrent insert are caught on the next call to
     * {@link #isRevoked(String)} or the next {@link #revoke} sweep.
     */
    private void purgeExpired() {
        Instant now = Instant.now();
        for (Map.Entry<String, Instant> entry : revoked.entrySet()) {
            if (entry.getValue().isBefore(now)) {
                // Conditional remove: only removes if the value hasn't changed
                // since we read it. Safe under concurrent updates.
                revoked.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
