package org.example.API;

import java.util.UUID;

import org.example.ApplicationLayer.ITokenBlacklist;
import org.example.ApplicationLayer.JwtService;
import org.example.ApplicationLayer.UserService;
import org.example.ApplicationLayer.dto.ApiResponse;
import org.example.ApplicationLayer.dto.AuthResponse;
import org.example.ApplicationLayer.dto.UserDTOs.LoginRequest;
import org.example.ApplicationLayer.dto.UserDTOs.RegisterRequest;
import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    private final UserService userService;
    private final JwtService jwtService;
    private final ITokenBlacklist tokenBlacklist;

    public UserController(UserService userService, JwtService jwtService, ITokenBlacklist tokenBlacklist) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.tokenBlacklist = tokenBlacklist;
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
            String token = jwtService.generateToken(guest.userId, guest.username, guest.role);
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
            String token = jwtService.generateToken(user.userId, user.username, user.role);
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
            UserResponse user = userService.login(request);
            String token = jwtService.generateToken(user.userId, user.username, user.role);
            AuthResponse body = new AuthResponse(true, "Login successful", token, user);
            return ResponseEntity.ok(ApiResponse.success("Login successful", body));
        } catch (IllegalArgumentException e) {
            // Invalid credentials → 401 Unauthorized (more specific than 400)
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
     * Reads the authenticated user id from the request (set by JwtAuthFilter)
     * and flips the domain status. After this call the client should discard
     * the token. Because JWTs are stateless, no server-side invalidation is
     * performed; the token will continue to validate until it expires.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<UserResponse>> logout(HttpServletRequest httpRequest) {
        try {
            UUID memberId = (UUID) httpRequest.getAttribute("userId");
            if (memberId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Missing or invalid token"));
            }
            UserResponse user = userService.logout(memberId);

            String authHeader = httpRequest.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring("Bearer ".length()).trim();
                Claims claims = jwtService.parseAndValidate(token);
                if (claims.getId() != null && claims.getExpiration() != null) {
                    tokenBlacklist.revoke(claims.getId(), claims.getExpiration().toInstant());
                }
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
