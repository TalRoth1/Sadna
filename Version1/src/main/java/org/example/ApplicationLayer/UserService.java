package org.example.ApplicationLayer;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.ApplicationLayer.dto.UserDTOs.LoginRequest;
import org.example.ApplicationLayer.dto.UserDTOs.RegisterRequest;
import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserRole;
import org.example.DomainLayer.UserAggregate.UserStatus;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private static final Logger logger = Logger.getLogger(UserService.class.getName());

    /**
     * A bcrypt-shaped placeholder hash used during login when the supplied email
     * doesn't match any user. We still run verifyPassword against this dummy so
     * the response time of a "wrong email" attempt is similar to a "wrong
     * password" attempt — closing the user-enumeration timing side channel.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$0000000000000000000000.0000000000000000000000000000";

    /**
     * Lock for the register/login/logout lifecycle. The in-memory IUserRepository
     * uses a plain HashMap, so without a lock two concurrent registrations could
     * both pass existsByEmail() and both succeed (creating duplicates). The same
     * lock also protects login/logout from interleaving with a concurrent
     * registration on the same email.
     *
     * This is a coarse-grained, single-JVM lock. With a real database we'd
     * replace this with a UNIQUE constraint on email + handling the resulting
     * exception, plus transactional repositories.
     */
    private final Object userLifecycleLock = new Object();

    private final IUserRepository userRepository;
    private final IAuthenticationGateway authGateway;
    private final INotifier notifier;

    public UserService(IUserRepository userRepository, IAuthenticationGateway authGateway, INotifier notifier) {
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.notifier = notifier;
    }

    /**
     * Creates a fresh anonymous visitor (guest) and persists it.
     *
     * Called by UserController on the first visit so the front-end can attach
     * a session token to a stable UUID even before the visitor logs in or
     * registers. Issuing the JWT itself is the controller's job — this method
     * only deals with the User aggregate.
     */
    public UserResponse enterAsGuest() {
        User guest = new User(UUID.randomUUID());
        userRepository.add(guest);
        return toResponse(guest);
    }

    public UserResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        if (request.email == null || request.email.isBlank()
                || request.plainPassword == null || request.plainPassword.isBlank()
                || request.username == null || request.username.isBlank()) {
            throw new IllegalArgumentException("Missing details.");
        }

        // Run input validation outside the lock — it's the heavy/expensive check
        // and doesn't touch the repository.
        if (!authGateway.verifyUserDetails(
                request.email,
                request.plainPassword,
                request.age,
                request.username)) {
            throw new IllegalArgumentException("One or more of the details is incorrect.");
        }

        // Critical section: existence-check + add must be atomic to prevent two
        // concurrent registrations with the same email or username from both
        // passing the check and both succeeding.
        synchronized (userLifecycleLock) {
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
            newUser.login(); // users created via registration are immediately logged in
            userRepository.add(newUser);

            // Log only the new user's UUID — no PII (no email, no username).
            logger.info("Registered new user id=" + newUser.getId());

            return toResponse(newUser);
        }
    }

    public UserResponse login(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        if (request.email == null || request.email.isBlank()
                || request.plainPassword == null || request.plainPassword.isBlank()) {
            throw new IllegalArgumentException("Email or password is empty.");
        }

        synchronized (userLifecycleLock) {
            // Look the user up *without* short-circuiting on "not found". We always
            // proceed to verifyPassword (against a dummy hash if necessary) so the
            // response time can't be used to enumerate which emails exist.
            User user = userRepository.findByEmail(request.email).orElse(null);
            String hashToCheck = (user != null) ? user.getPasswordHash() : DUMMY_PASSWORD_HASH;

            boolean isPasswordCorrect;
            try {
                isPasswordCorrect = authGateway.verifyPassword(request.plainPassword, hashToCheck);
            } catch (Exception ex) {
                // Defensive: if the gateway can't handle the dummy hash (different
                // scheme, malformed input, ...) treat it as "wrong password" rather
                // than leaking a 500 that reveals the email doesn't exist.
                isPasswordCorrect = false;
            }

            if (user == null || !isPasswordCorrect) {
                // Log only that *a* login attempt failed, no email/username PII.
                logger.warning("Failed login attempt");
                throw new IllegalArgumentException("Incorrect email or password.");
            }

            // Idempotent: if the user is already LOGGED_IN, that's fine — just
            // issue a fresh token (the controller does that after we return).
            // Calling user.login() in that state would throw IllegalStateException,
            // which is overly strict and traps users whose previous logout failed
            // silently (e.g. a 401 from a wiped-token interceptor on the client).
            if (user.getStatus() != UserStatus.LOGGED_IN) {
                user.login();
            } else {
                logger.info("Login re-issued for already-logged-in user id=" + user.getId());
            }
            return toResponse(user);
        }
    }

    public UserResponse logout(UUID memberId) {
        if (memberId == null) {
            logger.warning("Logout attempt with null memberId");
            throw new IllegalArgumentException("Member ID is required");
        }

        synchronized (userLifecycleLock) {
            User user = userRepository.getUser(memberId)
                    .orElseThrow(() -> new IllegalArgumentException("User does not exist."));

            // Guests were never "logged in" (their status is NOT_LOGGED_IN from
            // construction). Treat a logout call from a guest as a no-op so the
            // client gets a clean response instead of a 409. The controller will
            // still hand them a fresh guest token, which is the only real outcome
            // of logout from a guest's point of view.
            if (user.getRole() == UserRole.GUEST) {
                logger.info("Logout no-op for guest id=" + memberId);
                return toResponse(user);
            }

            // Idempotent: if the user is already NOT_LOGGED_IN, return cleanly
            // instead of letting user.logout() throw IllegalStateException.
            // This makes double-logout (e.g. an effect re-running, or a client
            // retry) safe instead of erroring out with a 500.
            if (user.getStatus() == UserStatus.NOT_LOGGED_IN) {
                logger.info("Logout no-op for already-logged-out user id=" + memberId);
                return toResponse(user);
            }

            user.logout();
            // No userRepository.add(user) here — the in-memory repo holds the same
            // reference under the user's UUID, so add() is a no-op. With a JPA-style
            // repo this would be replaced by an explicit save() inside a transaction.

            logger.info("Logged out user id=" + memberId);
            return toResponse(user);
        }
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus().toString(),
                user.getRole().toString(),
                user.getAge());
    }

    public void adminMessage(String username, String message) {
        try {
            if (username == null)
                throw new IllegalArgumentException("Username Is null");
            else if (!userRepository.isSystemAdmin(username))
                throw new IllegalArgumentException("User is not admin");
            else {
                for (Map.Entry<UUID, User> user : userRepository.getAllUsers().entrySet()) {
                    notifier.notifyUser(user.getKey(), message);
                }
            }
            logger.info("Admin: " + username + " sent message:" + message);
        } catch (Exception e) {
            logger.severe(e.toString());
            throw e;
        }
    }
}