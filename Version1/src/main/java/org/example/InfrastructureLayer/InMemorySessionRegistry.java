package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.ActiveSession;
import org.example.ApplicationLayer.IActiveSessionRegistry;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link IActiveSessionRegistry}.
 *
 * <h2>Atomicity guarantee</h2>
 * <p>The {@link #replaceSession(UUID, ActiveSession)} contract requires that
 * the swap — store the new session, return the old one — is atomic with
 * respect to concurrent calls for the same {@code userId}. Two concurrent
 * logins for the same account must never both see an empty slot; one of
 * them must observe the other's session as the "previous" value so that
 * it is correctly revoked.
 *
 * <p>{@link ConcurrentHashMap#put(Object, Object)} satisfies this contract:
 * it atomically associates the new value with the key and returns whatever
 * value was there before, in a single CAS-protected operation. No external
 * synchronization is needed.
 *
 * <h2>Scope</h2>
 * <p>Single-JVM only. For a horizontally scaled deployment replace this
 * bean with a Redis-backed implementation that uses {@code SET key value GET}
 * (Redis 6.2+) or {@code GETSET} for the same atomic swap. The
 * {@link IActiveSessionRegistry} interface contract is unchanged; no
 * consumer code needs to be modified.
 */
public class InMemorySessionRegistry implements IActiveSessionRegistry {

    /**
     * Stores at most one {@link ActiveSession} per user UUID.
     *
     * <p>{@code ConcurrentHashMap} is chosen over a plain {@code HashMap}
     * because:
     * <ul>
     *   <li>{@code put} is atomic (CAS-level) for a given key, which is the
     *       core requirement for the replace-and-return semantics.</li>
     *   <li>Concurrent reads for different users never block each other.</li>
     * </ul>
     */
    private final ConcurrentHashMap<UUID, ActiveSession> sessions =
            new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     *
     * <p>Implemented via {@link ConcurrentHashMap#put}, which atomically
     * stores {@code newSession} and returns the previous mapping (or
     * {@code null} if the slot was empty). The returned value is wrapped
     * in an {@link Optional} per the interface contract.
     *
     * @param userId     the user whose session slot is being updated;
     *                   must not be {@code null}
     * @param newSession the session to store; must not be {@code null}
     * @return the session that was displaced, or {@link Optional#empty()}
     *         if no prior session existed for this user
     */
    @Override
    public Optional<ActiveSession> replaceSession(UUID userId, ActiveSession newSession) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (newSession == null) {
            throw new IllegalArgumentException("newSession must not be null");
        }
        ActiveSession previous = sessions.put(userId, newSession);
        return Optional.ofNullable(previous);
    }
}
