package org.example.ApplicationLayer;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.ApplicationLayer.dto.AuthResponse;
import org.example.ApplicationLayer.dto.TokenClaims;
import org.example.ApplicationLayer.dto.UserDTOs.LoginRequest;
import org.example.ApplicationLayer.dto.UserDTOs.RegisterRequest;
import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.UserAggregate.User;
import org.springframework.stereotype.Service;

@Service
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

    public UserService(IUserRepository userRepository, IAuthenticationGateway authGateway) {
        this(userRepository, authGateway, null, null);
    }

    // ================================================================
    // New DTO API - used by the new controllers
    // ================================================================

    public UserResponse register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        if (request.email == null || request.email.isBlank()
                || request.plainPassword == null || request.plainPassword.isBlank()
                || request.username == null || request.username.isBlank()) {
            throw new IllegalArgumentException("Missing details.");
        }

        if (userRepository.existsByEmail(request.email)) {
            throw new IllegalArgumentException("User email already exists.");
        }

        if (!authGateway.verifyUserDetails(
                request.email,
                request.plainPassword,
                request.age,
                request.username)) {
            throw new IllegalArgumentException("One or more of the details is incorrect.");
        }

        String hashedPassword = authGateway.hashPassword(request.plainPassword);
        User newUser = new User(
                UUID.randomUUID(),
                request.username,
                request.email,
                hashedPassword,
                request.age
        );

        userRepository.add(newUser);
        return toResponse(newUser);
    }

    public UserResponse login(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        if (request.email == null || request.email.isBlank()
                || request.plainPassword == null || request.plainPassword.isBlank()) {
            throw new IllegalArgumentException("Email or password is empty.");
        }

        User user = userRepository.findByEmail(request.email)
                .orElseThrow(() -> new IllegalArgumentException("Incorrect email or password."));

        boolean isPasswordCorrect =
                authGateway.verifyPassword(request.plainPassword, user.getPasswordHash());

        if (!isPasswordCorrect) {
            logger.warning("Failed login attempt for user: " + request.email);
            throw new IllegalArgumentException("Incorrect email or password.");
        }

        user.login();
        userRepository.add(user);

        return toResponse(user);
    }

    public UserResponse logout(UUID memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("Member ID is required");
        }

        User user = userRepository.getUser(memberId)
                .orElseThrow(() -> new IllegalArgumentException("User does not exist."));

        user.logout();
        userRepository.add(user);

        return toResponse(user);
    }

    // ================================================================
    // Legacy DTO API - kept for old tests / old callers / JWT flow
    // ================================================================

    public AuthResponse register(org.example.ApplicationLayer.dto.RegisterRequest request) {
        try {
            if (request == null
                    || request.email == null || request.email.isBlank()
                    || request.plainPassword == null || request.plainPassword.isBlank()
                    || request.username == null || request.username.isBlank()) {
                return new AuthResponse(false, "Missing details.", null);
            }

            if (userRepository.existsByEmail(request.email)) {
                return new AuthResponse(false, "User Email is already exist.", null);
            }

            if (!authGateway.verifyUserDetails(
                    request.email,
                    request.plainPassword,
                    request.age,
                    request.username)) {
                return new AuthResponse(false, "One or more of the details is incorrect.", null);
            }

            String hashedPassword = authGateway.hashPassword(request.plainPassword);
            User newUser = new User(
                    UUID.randomUUID(),
                    request.username,
                    request.email,
                    hashedPassword,
                    request.age
            );

            userRepository.add(newUser);
            return new AuthResponse(true, "Register Successfully", newUser.getId());

        } catch (Exception e) {
            return new AuthResponse(false, "Register failed: system exception", null);
        }
    }

    public AuthResponse login(org.example.ApplicationLayer.dto.LoginRequest request) {
        try {
            if (request == null
                    || request.email == null || request.email.isBlank()
                    || request.plainPassword == null || request.plainPassword.isBlank()) {
                return new AuthResponse(false, "email or pass is empty", null);
            }

            User user = userRepository.findByEmail(request.email).orElse(null);
            if (user == null) {
                return new AuthResponse(false, "incorrect email or password.", null);
            }

            boolean isPasswordCorrect =
                    authGateway.verifyPassword(request.plainPassword, user.getPasswordHash());

            if (!isPasswordCorrect) {
                logger.info("Failed login attempt for user: " + request.email);
                return new AuthResponse(false, "incorrect email or password", null);
            }

            String token = null;
            String tokenType = null;
            long ttl = 0L;

            if (tokenService != null) {
                String role = userRepository.existsAdmin(user.getId()) ? "ADMIN" : "MEMBER";
                token = tokenService.issueAccessToken(user, role);
                tokenType = BEARER;
                ttl = tokenService.getAccessTokenTtlSeconds();
            } else {
                user.login();
                userRepository.add(user);
            }

            return new AuthResponse(true, "Login successfully", user.getId(), token, tokenType, ttl);

        } catch (Exception e) {
            return new AuthResponse(false, "Login failed: system exception", null);
        }
    }

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

    public Optional<TokenClaims> validateToken(String rawToken) {
        if (tokenService == null) {
            return Optional.empty();
        }

        Optional<TokenClaims> claims = tokenService.parseAndVerify(rawToken);

        if (claims.isEmpty()) {
            return Optional.empty();
        }

        TokenClaims tokenClaims = claims.get();

        if (tokenBlacklist != null
                && tokenClaims.getJti() != null
                && tokenBlacklist.isRevoked(tokenClaims.getJti())) {
            return Optional.empty();
        }

        return claims;
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus().toString(),
                user.getRole().toString(),
                user.getAge()
        );
    }
}