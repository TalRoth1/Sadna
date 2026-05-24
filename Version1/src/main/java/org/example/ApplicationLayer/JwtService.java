package org.example.ApplicationLayer;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JwtService — issues, parses, and validates JSON Web Tokens.
 *
 * <h2>Phase 2 — JWT Contract Refactoring</h2>
 *
 * <p>The old {@code generateToken} method returned a raw {@link String}.
 * This was a leaky contract: callers that needed the JWT's {@code jti}
 * (to register a session) or {@code expiresAt} (to set a blacklist
 * entry's TTL) had to re-parse the token they had just created, which is
 * redundant, error-prone, and couples callers to the token's internal
 * structure.
 *
 * <p>{@code generateToken} has been <strong>replaced entirely</strong>
 * (not overloaded) by {@link #mintSession(UUID, String, String)}, which
 * returns a {@link MintedToken} value object. The three pieces of session
 * metadata ({@code token}, {@code jti}, {@code expiresAt}) travel together
 * from the moment of minting, eliminating any need for callers to
 * re-extract them.
 *
 * <p>No overload of the old method is provided. Leaving {@code generateToken}
 * as an alternative would allow new code to accidentally call the weaker
 * form, accumulating the same technical debt we are removing. The compiler
 * now enforces the correct API.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Sign tokens with a server-side HMAC-SHA256 secret.</li>
 *   <li>Encode user identity ({@code userId}, {@code username}, {@code role})
 *       as JWT claims.</li>
 *   <li>Enforce expiry on {@link #parseAndValidate(String)}.</li>
 *   <li>Consult {@link ITokenBlacklist} so that explicitly revoked tokens
 *       are rejected even before they expire.</li>
 *   <li>Expose {@link #parseAllowingExpired(String)} for the logout endpoint,
 *       which still needs the user identity from an expired token.</li>
 * </ul>
 *
 * <p>This class knows nothing about users, repositories, or HTTP — it
 * is a pure cryptographic utility consumed by {@link SessionService} and
 * {@link org.example.API.JwtAuthFilter}.
 */
@Service
public class JwtService {

    private final SecretKey     signingKey;
    private final long          expirationMs;
    private final ITokenBlacklist blacklist;

    public JwtService(
            @Value("${jwt.secret:change-me-please-this-is-a-development-only-default-secret-key-1234567890}")
            String secret,
            @Value("${jwt.expiration-ms:3600000}")
            long expirationMs,
            ITokenBlacklist blacklist) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey   = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
        this.blacklist    = blacklist;
    }

    // ------------------------------------------------------------------
    // Value object — bundles all session metadata produced at mint time
    // ------------------------------------------------------------------

    /**
     * Immutable value object returned by {@link #mintSession}.
     *
     * <p>Carrying {@code token}, {@code jti}, and {@code expiresAt}
     * together means that {@link SessionService} can build an
     * {@link ActiveSession} and register it with {@link IActiveSessionRegistry}
     * without ever having to re-parse the token string.
     *
     * @param token     the compact JWT string to send to the client
     * @param jti       the unique JWT id claim; used as the key in
     *                  {@link ITokenBlacklist}
     * @param expiresAt the token's expiry instant; used as the TTL for
     *                  the blacklist entry so it auto-evicts
     */
    public record MintedToken(String token, String jti, Instant expiresAt) {
        public MintedToken {
            if (token     == null || token.isBlank())
                throw new IllegalArgumentException("MintedToken.token must not be blank");
            if (jti       == null || jti.isBlank())
                throw new IllegalArgumentException("MintedToken.jti must not be blank");
            if (expiresAt == null)
                throw new IllegalArgumentException("MintedToken.expiresAt must not be null");
        }
    }

    // ------------------------------------------------------------------
    // Token minting
    // ------------------------------------------------------------------

    /**
     * Mints a new signed JWT for the given user identity and returns all
     * session metadata as a {@link MintedToken}.
     *
     * <p>A fresh {@link UUID} is assigned as the {@code jti} on every
     * call, guaranteeing that each token can be individually revoked via
     * {@link ITokenBlacklist} without affecting other tokens for the same
     * user.
     *
     * @param userId   the user's UUID; becomes the JWT {@code sub} claim
     * @param username the user's display name; stored as a custom claim
     *                 ({@code "username"}); may be {@code null} for guests
     * @param role     the user's role (e.g. {@code "GUEST"}, {@code "MEMBER"});
     *                 stored as a custom claim ({@code "role"});
     *                 must not be {@code null} or blank
     * @return a {@link MintedToken} containing the compact JWT string,
     *         its unique {@code jti}, and its expiry instant
     * @throws IllegalArgumentException if {@code userId} or {@code role}
     *                                  is {@code null}/blank
     */
    public MintedToken mintSession(UUID userId, String username, String role) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }

        String  jti    = UUID.randomUUID().toString();
        Date    now    = new Date();
        Date    expiry = new Date(now.getTime() + expirationMs);

        String token = Jwts.builder()
                .id(jti)
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();

        return new MintedToken(token, jti, expiry.toInstant());
    }

    // ------------------------------------------------------------------
    // Token validation
    // ------------------------------------------------------------------

    /**
     * Parses and fully validates the given token.
     *
     * <p>Rejects the token if any of the following are true:
     * <ul>
     *   <li>The signature does not match.</li>
     *   <li>The token has expired.</li>
     *   <li>The {@code jti} is present in the {@link ITokenBlacklist}.</li>
     * </ul>
     *
     * @param token compact JWT string (without the {@code "Bearer "} prefix)
     * @return the token's claims if all checks pass
     * @throws JwtException             if the token is invalid for any reason
     * @throws IllegalArgumentException if {@code token} is null/blank
     */
    public Claims parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is required");
        }
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String jti = claims.getId();
        if (jti != null && blacklist != null && blacklist.isRevoked(jti)) {
            throw new JwtException("Token has been revoked");
        }
        return claims;
    }

    /**
     * Parses a token, accepting it even if it has expired.
     *
     * <p>Used by {@code POST /api/users/logout} where we still want the
     * user identity even after the token's lifetime has lapsed. Signature
     * verification, malformedness, and blacklist checks are still enforced
     * — only the {@code exp} constraint is relaxed.
     *
     * @param token compact JWT string; {@code null}/blank returns {@code null}
     * @return the claims, or {@code null} if the token is missing, malformed,
     *         tampered-with, or revoked
     */
    public Claims parseAllowingExpired(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return parseAndValidate(token);
        } catch (ExpiredJwtException expired) {
            Claims claims = expired.getClaims();
            if (claims != null && claims.getId() != null
                    && blacklist != null && blacklist.isRevoked(claims.getId())) {
                return null;
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Convenience extractors (used by JwtAuthFilter)
    // ------------------------------------------------------------------

    /** Extracts the user UUID from a valid token's {@code sub} claim. */
    public UUID extractUserId(String token) {
        return UUID.fromString(parseAndValidate(token).getSubject());
    }

    /** Extracts the {@code username} custom claim from a valid token. */
    public String extractUsername(String token) {
        Object u = parseAndValidate(token).get("username");
        return u == null ? null : u.toString();
    }

    /** Extracts the {@code role} custom claim from a valid token. */
    public String extractRole(String token) {
        Object r = parseAndValidate(token).get("role");
        return r == null ? null : r.toString();
    }

    /** Returns {@code true} iff the token is currently valid (signed + unexpired + not revoked). */
    public boolean isValid(String token) {
        try {
            parseAndValidate(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
