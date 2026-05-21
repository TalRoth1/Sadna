package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.AuthResponse;
import org.example.ApplicationLayer.dto.LoginRequest;
import org.example.ApplicationLayer.dto.RegisterRequest;
import org.example.ApplicationLayer.dto.TokenClaims;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.UserAggregate.User;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class UserService {
    private static final Logger logger = Logger.getLogger(UserService.class.getName());
    private static final String BEARER = "Bearer";

    private final IUserRepository userRepository;
    private final IAuthenticationGateway authGateway;
    private final ITokenService tokenService;
    private final ITokenBlacklist tokenBlacklist;

    public UserService(IUserRepository userRepository,
                       IAuthenticationGateway authGateway,
                       ITokenService tokenService,
                       ITokenBlacklist tokenBlacklist) {
        this.userRepository = userRepository;
        this.authGateway = authGateway;
        this.tokenService = tokenService;
        this.tokenBlacklist = tokenBlacklist;
    }

    /**
     * Backward-compatible constructor used by older tests / callers that
     * don't yet wire the JWT collaborators. Login/logout still work but no
     * token is issued and no jti is blacklisted.
     */
    public UserService(IUserRepository userRepository, IAuthenticationGateway authGateway) {
        this(userRepository, authGateway, null, null);
    }

    // =====================================================================
    // Register
    // =====================================================================

    public AuthResponse register(RegisterRequest request) {
        try {
            if (request.email == null || request.email.isEmpty() || request.plainPassword == null) {
                return new AuthResponse(false, "Missing details.", null);
            }
            if (userRepository.existsByEmail(request.email)) {
                return new AuthResponse(false, "User Email is already exist.", null);
            }
            if (!authGateway.verifyUserDetails(request.email, request.plainPassword, request.age, request.username)) {
                return new AuthResponse(false, "One or more of the details is incorrect.", null);
            }

            String hashedPassword = authGateway.hashPassword(request.plainPassword);
            UUID newUserId = UUID.randomUUID();
            User newUser = new User(newUserId, request.username, request.email, hashedPassword, request.age);

            userRepository.add(newUser);
            return new AuthResponse(true, "Register Successfully", newUser.getId());
        } catch (Exception e) {
            return new AuthResponse(false, "Register failed: system exception", null);
        }
    }

    // =====================================================================
    // Login — verify password, issue JWT
    // =====================================================================

    public AuthResponse login(LoginRequest request) {
        try {
            if (request.email == null || request.plainPassword == null) {
                return new AuthResponse(false, "email or pass is empty", null);
            }
            User user = userRepository.findByEmail(request.email).orElse(null);
            if (user == null) {
                return new AuthResponse(false, "incorrect email or password.", null);
            }
            boolean isPasswordCorrect = authGateway.verifyPassword(request.plainPassword, user.getPasswordHash());
            if (!isPasswordCorrect) {
                logger.info("Failed login attempt for user: " + request.email);
                return new AuthResponse(false, "incorrect email or password", null);
            }

            // No aggregate-state mutation: with JWT, the proof of "logged in"
            // is the signed token itself. Anything that previously asked
            // `user.getStatus() == LOGGED_IN` now inspects TokenClaims.

            String token = null;
            String tokenType = null;
            long ttl = 0L;
            if (tokenService != null) {
                String role = userRepository.existsAdmin(user.getId()) ? "ADMIN" : "MEMBER";
                token = tokenService.issueAccessToken(user, role);
                tokenType = BEARER;
                ttl = tokenService.getAccessTokenTtlSeconds();
            }
            return new AuthResponse(true, "Login successfully", user.getId(), token, tokenType, ttl);

        } catch (Exception e) {
            return new AuthResponse(false, "Login failed: system exception", null);
        }
    }

    // =====================================================================
    // Logout
    // =====================================================================

    /**
     * Stateless logout: the caller hands us the Bearer token they want to
     * invalidate. We verify the signature and blacklist the {@code jti} until
     * the token's original expiry, after which the same JWT will fail
     * {@link #validateToken(String)}.
     *
     * <p>If no blacklist is wired, this still succeeds — at that point the
     * client deleting the token is the only thing keeping it out of use.
     */
    public AuthResponse logoutWithToken(String rawToken) {
        try {
            if (tokenService == null) {
                return new AuthResponse(false, "Token service not configured.", null);
            }
            Optional<TokenClaims> opt = tokenService.parseAndVerify(rawToken);
            if (opt.isEmpty()) {
                return new AuthResponse(false, "Invalid or expired token.", null);
            }
            TokenClaims claims = opt.get();

            if (tokenBlacklist != null && claims.getJti() != null && claims.getExpiresAt() != null) {
                tokenBlacklist.revoke(claims.getJti(), claims.getExpiresAt());
            }
            return new AuthResponse(true, "logout successfully", claims.getUserId());
        } catch (Exception e) {
            return new AuthResponse(false, "Logout failed: system exception.", null);
        }
    }

    /**
     * Legacy logout kept for callers that haven't migrated to JWT yet.
     * With JWT this can't actually invalidate a token (it has no jti to
     * blacklist), so it's a no-op success: the client should also delete
     * its stored token. Prefer {@link #logoutWithToken(String)}.
     */
    public AuthResponse logout(UUID memberId) {
        try {
            User user = userRepository.getUser(memberId).orElse(null);
            if (user == null) {
                return new AuthResponse(false, "Request denied: user does not exist.", null);
            }
            return new AuthResponse(true, "logout successfully", user.getId());
        } catch (Exception e) {
            return new AuthResponse(false, "Logout failed: system exception.", memberId);
        }
    }

    // =====================================================================
    // Helper used by future REST filters / interceptors
    // =====================================================================

    /**
     * Verify a Bearer token coming off an inbound HTTP request.
     * Returns the decoded claims when the token is valid, signed by us,
     * not expired, and not revoked. Empty otherwise.
     */
    public Optional<TokenClaims> validateToken(String rawToken) {
        if (tokenService == null) {
            return Optional.empty();
        }
        return tokenService.parseAndVerify(rawToken);
    }
}
