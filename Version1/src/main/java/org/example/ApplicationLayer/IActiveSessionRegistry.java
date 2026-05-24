package org.example.ApplicationLayer;

import java.util.Optional;
import java.util.UUID;

/**
 * Port (DIP): per-user active-session store.
 *
 * <h2>Contract</h2>
 * <p>The system allows at most <em>one</em> live session per authenticated
 * user at any time. This interface is the single point that enforces that
 * constraint:
 * <ul>
 *   <li>{@link #replaceSession(UUID, ActiveSession)} atomically sets the
 *       new session and returns the previous one (if any). The caller
 *       ({@link SessionService}) uses the returned value to revoke the
 *       old JWT via {@link ITokenBlacklist}.</li>
 * </ul>
 *
 * <h2>Thread-safety requirement</h2>
 * <p>Implementations <em>must</em> guarantee that
 * {@link #replaceSession(UUID, ActiveSession)} is atomic with respect to
 * concurrent calls for the <em>same</em> {@code userId}. Two concurrent
 * logins for the same account must not both believe they are displacing an
 * empty slot — one of them must observe the other's session as the
 * "previous" value so that it is correctly revoked.
 *
 * <h2>Distributed readiness</h2>
 * <p>The in-memory implementation ({@code InMemorySessionRegistry})
 * satisfies the contract for a single JVM. A multi-instance deployment
 * should provide a Redis-backed implementation using
 * {@code GETSET} / {@code SET ... GET} for the same atomic swap semantics.
 * {@link SessionService} does not need to change.
 */
public interface IActiveSessionRegistry {

    /**
     * Atomically registers {@code newSession} as the current active session
     * for {@code userId}, replacing any previously stored session.
     *
     * <p>The previously stored session — if one existed — is returned so
     * that the caller can revoke its JWT before the new session becomes
     * observable to the rest of the system.
     *
     * @param userId     the user whose session slot is being updated;
     *                   must not be {@code null}
     * @param newSession the incoming session to store; must not be
     *                   {@code null}
     * @return an {@link Optional} containing the session that was displaced,
     *         or {@link Optional#empty()} if the user had no prior session
     */
    Optional<ActiveSession> replaceSession(UUID userId, ActiveSession newSession);
}
