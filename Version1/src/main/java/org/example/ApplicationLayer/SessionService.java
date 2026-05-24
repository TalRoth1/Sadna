package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.UserDTOs.LoginRequest;
import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * SessionService — Application-layer Use Case Orchestrator for login.
 *
 * <h2>Responsibility (SRP)</h2>
 * <p>This class has exactly one job: orchestrate the full login use case.
 * It is the single place in the system that knows how the following
 * cross-cutting concerns compose around a credential check:
 * <ol>
 *   <li><b>Rate limiting</b> — per-account brute-force throttle.</li>
 *   <li><b>Credential verification</b> — delegated to {@link UserService}.</li>
 *   <li><b>Token minting</b> — delegated to {@link JwtService}.</li>
 *   <li><b>Single-session enforcement (kick-old)</b> — the previous JWT
 *       is revoked before the new one becomes observable.</li>
 * </ol>
 *
 * <p>{@link UserService} knows nothing about tokens or sessions.
 * {@link JwtService} knows nothing about users or rate limiting.
 * {@code SessionService} knows nothing about passwords or repositories.
 * Each class has one reason to change (Open/Closed, SRP).
 *
 * <h2>The login sequence</h2>
 * <pre>
 *  1. rateLimiter.checkAllowed(email)         // throws RateLimitExceededException if throttled
 *  2. userService.login(request)              // throws IllegalArgumentException if bad credentials
 *  3. rateLimiter.recordSuccess(email)        // clears the failure window for this account
 *  4. jwtService.mintSession(...)             // returns MintedToken(token, jti, expiresAt)
 *  5. registry.replaceSession(userId, new)    // atomically returns Optional<ActiveSession> prev
 *  6. if prev.isPresent():
 *       blacklist.revoke(prev.jti, prev.expiresAt)   // OLD token is killed
 *  7. return AuthResult(token, jti, user)
 * </pre>
 *
 * <h2>Ordering invariant for step 5 → 6</h2>
 * <p>{@code replaceSession} is called <em>before</em> {@code revoke}. This
 * is intentional: once {@code replaceSession} returns, the old session is no
 * longer "the" active session for this user. The subsequent {@code revoke}
 * then blacklists its JWT. If the order were reversed, there would be a
 * window where the old JWT had been revoked but was still the registered
 * "active" session — an inconsistent state. The
 * {@code SessionServiceTest.login_kickOld_replaceSessionAndRevokeAreOrderedCorrectly}
 * test pins this ordering with Mockito {@code InOrder}.
 *
 * <h2>Distributed readiness</h2>
 * <p>Every collaborator is injected via an interface:
 * {@link IActiveSessionRegistry}, {@link ITokenBlacklist},
 * {@link ILoginRateLimiter}, and the {@link IKeyedLock} inside
 * {@link UserService}. Swapping any one of them for a Redis-backed
 * implementation requires zero changes to this class.
 */
@Service
public class SessionService {

    private static final Logger logger =
            Logger.getLogger(SessionService.class.getName());

    private final UserService            userService;
    private final JwtService             jwtService;
    private final IActiveSessionRegistry sessionRegistry;
    private final ITokenBlacklist        tokenBlacklist;
    private final ILoginRateLimiter      rateLimiter;

    // ------------------------------------------------------------------
    // Constructor — all dependencies injected (DIP / testability)
    // ------------------------------------------------------------------

    public SessionService(UserService            userService,
                          JwtService             jwtService,
                          IActiveSessionRegistry sessionRegistry,
                          ITokenBlacklist        tokenBlacklist,
                          ILoginRateLimiter      rateLimiter) {
        this.userService     = userService;
        this.jwtService      = jwtService;
        this.sessionRegistry = sessionRegistry;
        this.tokenBlacklist  = tokenBlacklist;
        this.rateLimiter     = rateLimiter;
    }

    // ------------------------------------------------------------------
    // Return value — carries everything the caller (UserController) needs
    // ------------------------------------------------------------------

    /**
     * The result of a successful {@link #login} call.
     *
     * <p>Bundling {@code token}, {@code jti}, and {@code user} together
     * avoids the caller having to call multiple getters on different
     * objects — a common source of subtle bugs (e.g. extracting the
     * {@code jti} from the token string instead of from the mint result).
     *
     * @param token the compact JWT string to send to the client
     * @param jti   the JWT id claim; exposed so the controller can
     *              include it in the response for debugging / correlation
     * @param user  the authenticated user's DTO
     */
    public record AuthResult(String token, String jti, UserResponse user) {}

    // ------------------------------------------------------------------
    // Use case
    // ------------------------------------------------------------------

    /**
     * Executes the full login use case and returns an {@link AuthResult}.
     *
     * <p>See the class-level Javadoc for the exact seven-step sequence
     * and the ordering invariant between {@code replaceSession} and
     * {@code revoke}.
     *
     * @param request the login credentials; must not be {@code null}
     * @return an {@link AuthResult} containing the new JWT and the
     *         authenticated user's DTO
     * @throws RateLimitExceededException if the per-account failure quota
     *                                    has been exhausted
     * @throws IllegalArgumentException   if the credentials are invalid
     */
    public AuthResult login(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("LoginRequest must not be null");
        }

        // Normalise the key once — same normalisation as UserService uses
        // for its per-email keyed lock, so rate-limiter and lock keys align.
        final String accountKey = (request.email == null)
                ? null
                : request.email.toLowerCase().trim();

        // ── Step 1 ── Throttle check (short-circuits before any DB/hash work)
        rateLimiter.checkAllowed(accountKey);

        UserResponse user;
        try {
            // ── Step 2 ── Credential verification (may be slow — bcrypt)
            user = userService.login(request);

        } catch (Exception authFailure) {
            // ── Step 2 failure path ── Count the failed attempt, then rethrow.
            rateLimiter.recordFailure(accountKey);
            throw authFailure;
        }

        // ── Step 3 ── Successful auth: clear the failure window for this account
        rateLimiter.recordSuccess(accountKey);

        // ── Step 4 ── Mint a fresh JWT with a unique jti
        JwtService.MintedToken minted =
                jwtService.mintSession(user.userId, user.username, user.role);

        // ── Step 5 ── Register the new session; atomically retrieve the old one
        ActiveSession newSession = new ActiveSession(minted.jti(), minted.expiresAt());
        Optional<ActiveSession> previousSession =
                sessionRegistry.replaceSession(user.userId, newSession);

        // ── Step 6 ── Revoke the displaced session's JWT (kick-old-session)
        //             MUST happen AFTER replaceSession so there is no window
        //             where the old JWT has been revoked but is still the
        //             "active" session in the registry.
        previousSession.ifPresent(prev -> {
            tokenBlacklist.revoke(prev.jti(), prev.expiresAt());
            logger.info("Revoked previous session jti=" + prev.jti()
                    + " for user id=" + user.userId);
        });

        logger.info("Session started for user id=" + user.userId
                + " jti=" + minted.jti());

        // ── Step 7 ── Return the compound result to the caller
        return new AuthResult(minted.token(), minted.jti(), user);
    }
}
