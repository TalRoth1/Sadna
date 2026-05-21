package org.example.InfrastructureLayer;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.ApplicationLayer.ITokenBlacklist;
import org.example.ApplicationLayer.ITokenService;
import org.example.ApplicationLayer.dto.TokenClaims;
import org.example.DomainLayer.UserAggregate.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * jjwt 0.12-based implementation of {@link ITokenService}.
 *
 * <p>Signs with HS256 over an HMAC secret key (≥ 256 bits). The secret should
 * come from configuration (e.g. an env var like {@code JWT_SECRET}); never
 * commit a real secret to source control.
 *
 * <p>Claims layout:
 * <ul>
 *   <li>{@code sub} – user id (UUID)</li>
 *   <li>{@code email}, {@code username}</li>
 *   <li>{@code role} – {@code MEMBER} / {@code GUEST} / {@code ADMIN}</li>
 *   <li>{@code tokenType} – {@code ACCESS} / {@code GUEST} / {@code REFRESH}</li>
 *   <li>{@code jti}, {@code iat}, {@code exp}</li>
 * </ul>
 */
public class JjwtTokenService implements ITokenService {

    public static final String CLAIM_EMAIL      = "email";
    public static final String CLAIM_USERNAME   = "username";
    public static final String CLAIM_ROLE       = "role";
    public static final String CLAIM_TOKEN_TYPE = "tokenType";

    private final SecretKey signingKey;
    private final String issuer;
    private final long accessTokenTtlSeconds;
    private final long guestTokenTtlSeconds;
    private final ITokenBlacklist blacklist;

    public JjwtTokenService(String secret,
                            String issuer,
                            long accessTokenTtlSeconds,
                            long guestTokenTtlSeconds,
                            ITokenBlacklist blacklist) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.guestTokenTtlSeconds = guestTokenTtlSeconds;
        this.blacklist = blacklist;
    }

    @Override
    public String issueAccessToken(User user, String role) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Cannot issue token for null user");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_EMAIL,      user.getEmail());
        claims.put(CLAIM_USERNAME,   user.getUsername());
        claims.put(CLAIM_ROLE,       (role == null || role.isBlank()) ? "MEMBER" : role);
        claims.put(CLAIM_TOKEN_TYPE, TokenClaims.TokenType.ACCESS.name());
        return buildToken(user.getId().toString(), claims, accessTokenTtlSeconds);
    }

    @Override
    public String issueGuestToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_ROLE,       "GUEST");
        claims.put(CLAIM_TOKEN_TYPE, TokenClaims.TokenType.GUEST.name());
        return buildToken("guest:" + UUID.randomUUID(), claims, guestTokenTtlSeconds);
    }

    @Override
    public Optional<TokenClaims> parseAndVerify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims body = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(rawToken)
                    .getPayload();

            String jti = body.getId();
            if (jti != null && blacklist != null && blacklist.isRevoked(jti)) {
                return Optional.empty();
            }

            UUID userId = parseSubjectAsUuid(body.getSubject());
            TokenClaims.TokenType type = parseTokenType(body.get(CLAIM_TOKEN_TYPE, String.class));

            return Optional.of(new TokenClaims(
                    userId,
                    body.get(CLAIM_EMAIL, String.class),
                    body.get(CLAIM_USERNAME, String.class),
                    body.get(CLAIM_ROLE, String.class),
                    type,
                    jti,
                    body.getIssuedAt() == null ? null : body.getIssuedAt().toInstant(),
                    body.getExpiration() == null ? null : body.getExpiration().toInstant()
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    private String buildToken(String subject, Map<String, Object> claims, long ttlSeconds) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);
        return Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(claims)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private static UUID parseSubjectAsUuid(String subject) {
        if (subject == null) return null;
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            // Guest subjects look like "guest:<uuid>" — no real user id.
            return null;
        }
    }

    private static TokenClaims.TokenType parseTokenType(String value) {
        if (value == null) return TokenClaims.TokenType.ACCESS;
        try {
            return TokenClaims.TokenType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return TokenClaims.TokenType.ACCESS;
        }
    }
}
