package org.example.ApplicationLayer;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.ApplicationLayer.dto.UserDTOs.LoginRequest;
import org.example.ApplicationLayer.dto.UserDTOs.RegisterRequest;
import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;
import org.example.DomainLayer.Events.UserRegisteredEvent;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserRole;
import org.example.DomainLayer.UserAggregate.UserStatus;
import org.springframework.stereotype.Service;

/**
 * UserService — Application-layer use-case handler for identity lifecycle.
 *
 * <h2>Phase 1 Concurrency Refactoring</h2>
 *
 * <p>The previous implementation used a single JVM-wide {@code Object}
 * monitor ({@code userLifecycleLock}) shared across every call to
 * {@code register}, {@code login}, and {@code logout}. That coarse-grained
 * lock serialised <em>all</em> users through a single bottleneck, causing
 * login starvation under concurrent load: while one thread was running the
 * slow bcrypt {@code verifyPassword} check, every other user in the system
 * was blocked.
 *
 * <p>This refactoring replaces the global lock with an
 * {@link IKeyedLock}{@code <String>} injected via the constructor
 * (Dependency Inversion Principle). The lock is keyed on the normalised
 * email address for {@code register} and {@code login}, and on the user
 * UUID string for {@code logout}:
 *
 * <ul>
 *   <li>Two users logging in with <em>different</em> emails acquire
 *       different lock keys and therefore run fully in parallel —
 *       the {@code verifyPassword} calls overlap.</li>
 *   <li>Two login attempts for the <em>same</em> email are serialised,
 *       preserving the {@code User.status} invariant and closing the
 *       timing side-channel that reveals whether an account exists.</li>
 *   <li>A username-uniqueness race that a per-email lock cannot close
 *       (two threads with different emails, same username) is handled at
 *       the repository level: {@link IUserRepository#add} re-checks both
 *       email and username inside an internal lock before committing the
 *       insert.</li>
 * </ul>
 *
 * <h2>SOLID alignment</h2>
 * <ul>
 *   <li><b>SRP</b> — this class only orchestrates identity operations;
 *       token generation and request routing live elsewhere.</li>
 *   <li><b>DIP</b> — {@code IKeyedLock} is an interface; the production
 *       wiring uses {@code InMemoryKeyedLock}, tests inject a real or
 *       mock implementation without touching production code.</li>
 *   <li><b>OCP</b> — swapping to a distributed lock (Redis, ZooKeeper)
 *       requires only a new {@code IKeyedLock} implementation; this
 *       class is not modified.</li>
 * </ul>
 */
@Service
public class UserService {

    private static final Logger logger = Logger.getLogger(UserService.class.getName());

    /**
     * A bcrypt-shaped placeholder hash used during login when the supplied
     * email doesn't match any stored user. Running {@code verifyPassword}
     * against this dummy ensures that the response time of a "wrong email"
     * attempt is indistinguishable from a "wrong password" attempt, closing
     * the user-enumeration timing side-channel.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$0000000000000000000000.0000000000000000000000000000";

    private final IUserRepository   userRepository;
    private final IAuthenticationGateway authGateway;
    private final INotifier         notifier;
    private final EventPublisher    eventPublisher;

    /**
     * Per-key mutual exclusion.
     *
     * <p>Keys used in this class:
     * <ul>
     *   <li>{@code register} → {@code email.toLowerCase().trim()} — prevents
     *       two concurrent registrations for the same email from both passing
     *       the {@code existsByEmail} check.</li>
     *   <li>{@code login}    → {@code email.toLowerCase().trim()} — serialises
     *       concurrent logins for the same account so that
     *       {@code User.status} is never observed in a torn state.</li>
     *   <li>{@code logout}   → {@code memberId.toString()}        — serialises
     *       concurrent logouts for the same user.</li>
     * </ul>
     *
     * <p>Logins for <em>different</em> accounts hold different lock keys and
     * therefore execute in parallel. This is the core improvement over the
     * old global {@code synchronized} block.
     */
    private final IKeyedLock<String> keyedLock;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    /**
     * Primary constructor — used by Spring (auto-wired) and by tests.
     *
     * @param userRepository repository for {@link User} persistence
     * @param authGateway    password hashing and verification
     * @param notifier       sends domain events / notifications
     * @param keyedLock      per-email / per-userId mutual exclusion;
     *                       inject {@code InMemoryKeyedLock<String>} for
     *                       single-JVM deployments, a Redis-backed
     *                       implementation for clustered ones
     * @param eventPublisher shared application event bus; a
     *                       {@code UserRegisteredEvent} is published after
     *                       every successful registration so that
     *                       {@code SystemMetricsCollector} can record the rate
     */
    public UserService(IUserRepository        userRepository,
                       IAuthenticationGateway authGateway,
                       INotifier              notifier,
                       IKeyedLock<String>     keyedLock,
                       EventPublisher         eventPublisher) {
        this.userRepository  = userRepository;
        this.authGateway     = authGateway;
        this.notifier        = notifier;
        this.keyedLock       = keyedLock;
        this.eventPublisher  = eventPublisher;
    }

    // ------------------------------------------------------------------
    // Use cases
    // ------------------------------------------------------------------

    /**
     * Creates a fresh anonymous visitor (guest) and persists it.
     *
     * <p>Guests are identified by a freshly generated UUID, so no
     * uniqueness conflict is possible; no lock is acquired.
     *
     * @return a DTO representing the new guest
     */
    public UserResponse enterAsGuest() {
        User guest = new User(UUID.randomUUID());
        userRepository.add(guest);
        return toResponse(guest);
    }

    /**
     * Registers a new member account.
     *
     * <p>Input validation (format, policy rules) runs <em>outside</em> the
     * lock because it is CPU-bound and does not touch the repository.
     * The critical section — existence check + hash + insert — runs inside
     * the email-keyed lock to guarantee atomicity without blocking logins
     * for unrelated accounts.
     *
     * @param request registration payload; must not be {@code null}
     * @return DTO of the newly created, immediately logged-in user
     * @throws IllegalArgumentException if any field is missing, invalid,
     *                                  or already taken
     */
    public UserResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (request.email == null        || request.email.isBlank()
                || request.plainPassword == null || request.plainPassword.isBlank()
                || request.username == null      || request.username.isBlank()) {
            throw new IllegalArgumentException("Missing details.");
        }

        // Heavy validation (format checks, age policy, etc.) runs outside the
        // lock — it's pure computation with no repository access.
        if (!authGateway.verifyUserDetails(
                request.email,
                request.plainPassword,
                request.age,
                request.username)) {
            // Surface the specific field that failed so the user knows what
            // to fix, instead of a single opaque "details are incorrect".
            throw new IllegalArgumentException(describeInvalidDetails(request));
        }

        // Normalise the key once; re-used for both the lock and the lookup.
        final String emailKey = request.email.toLowerCase().trim();

        // Critical section: existence checks + hash + insert must be atomic
        // w.r.t. other threads registering with the same email.
        //
        // NOTE: A concurrent thread registering with a DIFFERENT email but
        // the SAME username will not contend on this lock. That race is
        // closed at the repository layer: IUserRepository.add() re-checks
        // both email and username inside its own internal lock before
        // committing the insert.
        UserResponse response = keyedLock.withLock(emailKey, () -> {
            if (userRepository.existsByEmail(request.email)) {
                throw new IllegalArgumentException("User email already exists.");
            }
            if (userRepository.existsByUsername(request.username)) {
                throw new IllegalArgumentException("Username already exists.");
            }

            String hashedPassword = authGateway.hashPassword(request.plainPassword);
            User newUser = new User(
                    UUID.randomUUID(),
                    request.username,
                    request.email,
                    hashedPassword,
                    request.age);

            // Users created via registration are immediately active.
            newUser.login();

            // Repository.add() is the definitive uniqueness gate; it will
            // re-check under its own lock and throw IllegalArgumentException
            // if a concurrent thread has already claimed the username or email.
            userRepository.add(newUser);

            // Log only the UUID — no PII (no email, no username) in logs.
            logger.info("Registered new user id=" + newUser.getId());
            return toResponse(newUser);
        });

        // Publish outside the per-email lock so that the event handler
        // (SystemMetricsCollector) does not contend on the same lock key.
        eventPublisher.publish(new UserRegisteredEvent(response.userId));
        return response;
    }

    /**
     * Pinpoints which registration field failed {@code verifyUserDetails} so
     * the caller can return an actionable message. Mirrors the order/rules of
     * the active {@link IAuthenticationGateway}.
     */
    private String describeInvalidDetails(RegisterRequest request) {
        if (!authGateway.verifyEmail(request.email)) {
            return "Email address is invalid. Please use the format name@example.com.";
        }
        if (!authGateway.verifyPassword(request.plainPassword)) {
            return "Password is too short. Please choose a password with at least 8 characters.";
        }
        if (request.age < 0) {
            return "Age must be a positive number.";
        }
        if (request.username == null || request.username.isBlank()) {
            return "Username is required.";
        }
        return "One or more of the details is incorrect.";
    }

    /**
     * Authenticates a member and transitions their status to
     * {@link UserStatus#LOGGED_IN}.
     *
     * <p>The method is <em>idempotent</em> on an already-logged-in account:
     * if the user is already {@code LOGGED_IN}, a fresh token is issued
     * without attempting another status transition (which would throw
     * {@code IllegalStateException}). This handles the case where a previous
     * logout silently failed on the client side.
     *
     * <p>The timing side-channel defence: even when no user exists for the
     * supplied email, {@code verifyPassword} is called against a dummy hash.
     * This makes "wrong email" and "wrong password" attempts
     * indistinguishable by response time.
     *
     * @param request login credentials; must not be {@code null}
     * @return DTO of the authenticated user
     * @throws IllegalArgumentException if credentials are invalid
     */
    public UserResponse login(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (request.email == null        || request.email.isBlank()
                || request.plainPassword == null || request.plainPassword.isBlank()) {
            throw new IllegalArgumentException("Email or password is empty.");
        }

        final String emailKey = request.email.toLowerCase().trim();

        return keyedLock.withLock(emailKey, () -> {
            // Look the user up without short-circuiting on "not found".
            // We always proceed to verifyPassword (against a dummy hash if
            // necessary) so response time cannot be used to enumerate emails.
            User user = userRepository.findByEmail(emailKey).orElse(null);
            String hashToCheck = (user != null)
                    ? user.getPasswordHash()
                    : DUMMY_PASSWORD_HASH;

            if (user != null && user.getStatus() == UserStatus.REMOVED) {
                throw new IllegalArgumentException("This account was removed from the platform.");
            }

            boolean isPasswordCorrect;
            try {
                isPasswordCorrect = authGateway.verifyPassword(
                        request.plainPassword, hashToCheck);
            } catch (Exception ex) {
                // Defensive: if the gateway cannot handle the dummy hash
                // (different scheme, malformed input, …) treat it as a wrong
                // password rather than leaking a 500 that reveals the email
                // doesn't exist.
                isPasswordCorrect = false;
            }

            if (user == null || !isPasswordCorrect) {
                logger.warning("Failed login attempt");
                throw new IllegalArgumentException("Incorrect email or password.");
            }

            // Idempotent: re-issuing a token for an already-logged-in user is
            // intentional — it supports "login from a second device" without
            // kicking the first session (that is Phase 2's job via SessionService).
            if (user.getStatus() != UserStatus.LOGGED_IN)
            {
                user.login();
                userRepository.add(user);
            } else {
                logger.info("Login re-issued for already-logged-in user id="
                        + user.getId());
            }
            return toResponse(user);
        });
    }

    /**
     * Logs out the user identified by {@code memberId}.
     *
     * <p>This method is <em>tolerant</em>: calling it on a guest or on an
     * already-logged-out member is a no-op (returns cleanly instead of
     * throwing). This prevents double-logout (e.g. from a React
     * {@code useEffect} running twice) from producing a 500.
     *
     * @param memberId the UUID of the user to log out; must not be {@code null}
     * @return DTO of the logged-out user
     * @throws IllegalArgumentException if {@code memberId} is {@code null} or
     *                                  no such user exists
     */
    public UserResponse logout(UUID memberId) {
        if (memberId == null) {
            logger.warning("Logout attempt with null memberId");
            throw new IllegalArgumentException("Member ID is required");
        }

        // Key on the user's UUID string so that concurrent logouts for the
        // same account serialise, while logouts for different accounts proceed
        // in parallel.
        return keyedLock.withLock(memberId.toString(), () -> {
            User user = userRepository.getUser(memberId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("User does not exist."));

            // Guests were never "logged in" — treat logout as a no-op.
            if (user.getRole() == UserRole.GUEST) {
                logger.info("Logout no-op for guest id=" + memberId);
                return toResponse(user);
            }

            // Idempotent: double-logout (e.g. from a client retry) is safe.
            if (user.getStatus() == UserStatus.NOT_LOGGED_IN) {
                logger.info("Logout no-op for already-logged-out user id=" + memberId);
                return toResponse(user);
            }

            user.logout();
            userRepository.add(user);
            logger.info("Logged out user id=" + memberId);
            return toResponse(user);
        });
    }

    /**
     * Sends a system-wide message from an admin to every registered user.
     *
     * @param username the admin's username (checked against the admin store)
     * @param message  the message to broadcast
     * @throws IllegalArgumentException if the username is null or not an admin
     */
    public void adminMessage(String username, String message) {
        try {
            if (username == null) {
                throw new IllegalArgumentException("Username is null");
            }
            if (!userRepository.isSystemAdmin(username)) {
                throw new IllegalArgumentException("User is not admin");
            }
            for (Map.Entry<UUID, User> entry : userRepository.getAllUsers().entrySet()) {
                notifier.notifyUser(entry.getKey(), message);
            }
            logger.info("Admin " + username + " sent message: " + message);
        } catch (Exception e) {
            logger.severe(e.toString());
            throw e;
        }
    }

    /**
     * Returns the public DTO for the given user ID.
     *
     * <p>Used by {@code GET /api/users/me} so the frontend can validate
     * that its stored session is still live on the current server
     * instance (important after an in-memory restart where all user data
     * is wiped but old JWTs remain cryptographically valid).
     *
     * @param userId the user's UUID; must not be {@code null}
     * @return the user's public {@link UserResponse}
     * @throws IllegalArgumentException if {@code userId} is {@code null}
     *                                  or no such user exists in the repository
     */
    public UserResponse getUserById(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        User user = userRepository.getUser(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found: " + userId));
        return toResponse(user);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Converts a {@link User} aggregate root to its public DTO.
     *
     * <p>Admin status is resolved from the repository's admin map so the
     * front-end does not need a separate round-trip to distinguish a regular
     * member from a system administrator.
     */
    private UserResponse toResponse(User user) {
        boolean isAdmin =
                userRepository.existsAdmin(user.getId())
                        || (user.getEmail() != null && userRepository.isSystemAdmin(user.getEmail()))
                        || (user.getUsername() != null && userRepository.isSystemAdmin(user.getUsername()));

        String role = isAdmin ? "ADMIN" : user.getRole().toString();

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus().toString(),
                role,
                user.getAge(),
                isAdmin
        );
    }
}
