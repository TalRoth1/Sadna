package org.example.ApplicationLayer.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Decoded representation of a verified JWT.
 * Returned by {@code ITokenService.parseAndVerify} so the rest of the
 * application can identify the caller without re-parsing the token.
 */
public class TokenClaims {

    /** Distinguishes regular access tokens from guest / refresh tokens. */
    public enum TokenType {
        ACCESS,
        GUEST,
        REFRESH
    }

    private final UUID userId;
    private final String email;
    private final String username;
    private final String role;
    private final TokenType tokenType;
    private final String jti;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public TokenClaims(UUID userId,
                       String email,
                       String username,
                       String role,
                       TokenType tokenType,
                       String jti,
                       Instant issuedAt,
                       Instant expiresAt) {
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.role = role;
        this.tokenType = tokenType;
        this.jti = jti;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() { return userId; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public TokenType getTokenType() { return tokenType; }
    public String getJti() { return jti; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
