package org.example.ApplicationLayer;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JwtService
 *
 * Issues, parses, and validates JSON Web Tokens (JWTs) used as session tokens.
 *
 * Responsibilities:
 *   - Sign tokens with a server-side secret (HS256).
 *   - Encode user identity (userId, username, role) as claims.
 *   - Enforce expiry (configurable).
 *   - Parse incoming tokens and verify their signature + expiry.
 *
 * This class deliberately knows nothing about users, repositories, or HTTP.
 * It is consumed by the UserController (to mint tokens after login/register/guest)
 * and by the JwtAuthFilter (to validate tokens on incoming requests).
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;
    private final ITokenBlacklist blacklist;

    public JwtService(
            @Value("${jwt.secret:change-me-please-this-is-a-development-only-default-secret-key-1234567890}") String secret,
            @Value("${jwt.expiration-ms:3600000}") long expirationMs,
            ITokenBlacklist blacklist) {
        // HS256 needs at least 256 bits (32 bytes). The default above is long enough; if
        // a real secret is provided via properties/env it should also be at least 32 bytes.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
        this.blacklist = blacklist;
    }

    /**
     * Mint a new signed JWT for the given user identity.
     *
     * @param userId   the user's UUID (becomes the JWT subject)
     * @param username the user's display name (claim "username") — may be null for guests
     * @param role     the user's role, e.g. "GUEST" or "MEMBER"
     * @return compact JWT string suitable for Authorization: Bearer ...
     */
    public String generateToken(UUID userId, String username, String role) {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
            .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parse and validate the given token.
     *
     * @param token compact JWT string (without the "Bearer " prefix)
     * @return the token's claims if signature + expiry are valid
     * @throws JwtException if the token is malformed, tampered with, or expired
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
            throw new io.jsonwebtoken.JwtException("Token has been revoked");
        }

        return claims;
    }

    /** Convenience: returns the user id (JWT subject) from a valid token. */
    public UUID extractUserId(String token) {
        return UUID.fromString(parseAndValidate(token).getSubject());
    }

    /** Convenience: returns the username claim from a valid token. */
    public String extractUsername(String token) {
        Object u = parseAndValidate(token).get("username");
        return u == null ? null : u.toString();
    }

    /** Convenience: returns the role claim from a valid token. */
    public String extractRole(String token) {
        Object r = parseAndValidate(token).get("role");
        return r == null ? null : r.toString();
    }

    /** Returns true iff the token is currently valid (signature + not expired). */
    public boolean isValid(String token) {
        try {
            parseAndValidate(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Parse a token, accepting it even if it has expired.
     *
     * <p>Used by endpoints like {@code POST /api/users/logout} where we still
     * want the user identity that the token was minted with, even if the token
     * is no longer cryptographically "valid" for normal authentication.
     * Signature, malformedness and revocation are still enforced — only the
     * {@code exp} check is relaxed.
     *
     * @return the claims, or {@code null} if the token is missing/malformed
     *         /tampered-with/revoked.
     */
    public Claims parseAllowingExpired(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return parseAndValidate(token);
        } catch (ExpiredJwtException expired) {
            // ExpiredJwtException still carries the original claims — that's
            // exactly what we want here.
            Claims claims = expired.getClaims();
            // Still honour the blacklist: if the (now-expired) token was
            // explicitly revoked, treat it as unusable.
            if (claims != null && claims.getId() != null
                    && blacklist != null && blacklist.isRevoked(claims.getId())) {
                return null;
            }
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }
}
