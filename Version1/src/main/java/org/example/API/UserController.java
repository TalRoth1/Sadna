package org.example.API;

import java.util.UUID;

import org.example.ApplicationLayer.ITokenBlacklist;
import org.example.ApplicationLayer.JwtService;
import org.example.ApplicationLayer.RateLimitExceededException;
import org.example.ApplicationLayer.SessionService;
import org.example.ApplicationLayer.UserService;
import org.example.ApplicationLayer.dto.ApiResponse;
import org.example.ApplicationLayer.dto.AuthResponse;
import org.example.ApplicationLayer.dto.UserDTOs.LoginRequest;
import org.example.ApplicationLayer.dto.UserDTOs.RegisterRequest;
import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;

/**
 * UserController
 *
 * Every authentication endpoint returns ResponseEntity<ApiResponse<AuthResponse>>
 * with a consistent shape:
 *   { "success": boolean, "message": string, "data": { token, user, ... } | null }
 *
 * The token is a signed JWT the client should send back as
 *   Authorization: Bearer <token>
 * on every subsequent request to a protected endpoint.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService      userService;
    private final JwtService       jwtService;
    private final ITokenBlacklist  tokenBlacklist;
    private final SessionService   sessionService;

    public UserController(UserService     userService,
                          JwtService      jwtService,
                          ITokenBlacklist tokenBlacklist,
                          SessionService  sessionService) {
        this.userService    = userService;
        this.jwtService     = jwtService;
        this.tokenBlacklist = tokenBlacklist;
        this.sessionService = sessionService;
    }

    /**
     * GET /api/users/me
     *
     * Validates that the caller's JWT is still usable on this server instance
     * and that the user actually exists in the repository.
     *
     * This endpoint is intentionally NOT in JwtAuthFilter#PUBLIC_PATHS, so the
     * filter enforces token signature + expiry before this method runs.
     *
     * The extra repository lookup catches the "ghost login" scenario: after an
     * in-memory server restart the JWT is still cryptographically valid, but
     * all user data has been wiped. Without this check the frontend would read
     * stale localStorage and show the user as logged-in while every protected
     * API call silently fails.
     *
     * Returns:
     *   200 — token is valid AND the user exists in the repository.
     *   401 — no userId attribute (filter let an un-authed request through) or
     *          the user was not found in the repository (stale session).
     *   500 — unexpected error.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(HttpServletRequest httpRequest) {
        UUID userId = (UUID) httpRequest.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authenticated"));
        }
        try {
            UserResponse user = userService.getUserById(userId);
            return ResponseEntity.ok(ApiResponse.success("User found", user));
        } catch (IllegalArgumentException e) {
            // User not found in repository — session is stale (e.g. after restart).
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Session is no longer valid"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch user: " + e.getMessage()));
        }
    }

    /**
     * POST /api/users/guest
     *
     * Issues a guest identity + session token on first visit. The client should
     * call this on app load (when it has no stored token) and store the returned
     * token in memory / localStorage.
     */
    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<AuthResponse>> enterAsGuest() {
        try {
            UserResponse guest = userService.enterAsGuest();
            String token = jwtService.mintSession(guest.userId, guest.username, guest.role).token();
            AuthResponse body = new AuthResponse(true, "Guest session started", token, guest);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Guest session started", body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Guest session failed: system exception\n" + e.getMessage()));
        }
    }

    /**
     * POST /api/users/register
     *
     * Creates a new member account and immediately issues a member-level JWT.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@RequestBody RegisterRequest request) {
        try {
            UserResponse user = userService.register(request);
            String token = jwtService.mintSession(user.userId, user.username, user.role).token();
            AuthResponse body = new AuthResponse(true, "Registered successfully", token, user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Registered successfully", body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Register failed: system exception\n" + e.getMessage()));
        }
    }

    /**
     * POST /api/users/login
     *
     * Verifies credentials and issues a fresh member-level JWT.
     * The client should discard any previously held guest token and replace it
     * with the one returned here.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LoginRequest request) {
        try {
            SessionService.AuthResult result = sessionService.login(request);
            AuthResponse body = new AuthResponse(true, "Login successful", result.token(), result.user());
            return ResponseEntity.ok(ApiResponse.success("Login successful", body));
        } catch (RateLimitExceededException e) {
            // Too many failed attempts for this account → 429 Too Many Requests
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            // Invalid credentials → 401 Unauthorized
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Login failed: system exception\n" + e.getMessage()));
        }
    }

    /**
     * POST /api/users/logout
     *
     * Tolerant logout: this endpoint is in JwtAuthFilter#PUBLIC_PATHS, so the
     * filter never rejects it. We parse the bearer token *leniently* (accepting
     * expired tokens, returning null on bad ones) so that ending a session
     * works in every realistic state — fresh, expired, revoked, or even
     * missing.
     *
     * Behaviour matrix:
     *   - Valid token       → log the user out, revoke the token, 200.
     *   - Expired token     → log the user out (their identity is still in the
     *                         claims), don't bother revoking (already invalid), 200.
     *   - Revoked / bad     → can't extract a user id; treat as already-logged-out
     *                         success (200 with null user) so the client can move on.
     *   - No Authorization  → same as revoked/bad: 200, no-op.
     *
     * The point is: a logout call must NEVER trap the client in a half-state
     * where the server still thinks they're logged in but the client has
     * already discarded its token.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<UserResponse>> logout(HttpServletRequest httpRequest) {
        try {
            String authHeader = httpRequest.getHeader("Authorization");
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring("Bearer ".length()).trim();
            }

            Claims claims = (token == null) ? null : jwtService.parseAllowingExpired(token);
            if (claims == null) {
                // Missing / malformed / revoked token: nothing actionable on the
                // server. Returning 200 lets the client finish its local cleanup
                // without us cascading into a 401 + force-redirect loop.
                return ResponseEntity.ok(ApiResponse.success("Already logged out", null));
            }

            UUID memberId;
            try {
                memberId = UUID.fromString(claims.getSubject());
            } catch (Exception e) {
                return ResponseEntity.ok(ApiResponse.success("Already logged out", null));
            }

            UserResponse user = userService.logout(memberId);

            // Best-effort revocation: only blacklist a token that is still live.
            // Revoking an already-expired token is pointless (it's invalid anyway)
            // and just bloats the in-memory blacklist.
            if (claims.getId() != null && claims.getExpiration() != null
                    && claims.getExpiration().toInstant().isAfter(java.time.Instant.now())) {
                tokenBlacklist.revoke(claims.getId(), claims.getExpiration().toInstant());
            }

            return ResponseEntity.ok(ApiResponse.success("Logout successful", user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Logout failed: system exception\n" + e.getMessage()));
        }
    }
}
