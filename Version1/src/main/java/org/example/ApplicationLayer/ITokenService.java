package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.TokenClaims;
import org.example.DomainLayer.UserAggregate.User;

import java.util.Optional;

/**
 * Application-layer port for issuing and verifying JWTs.
 * The domain layer never depends on a concrete JWT library;
 * the implementation lives in {@code InfrastructureLayer}.
 */
public interface ITokenService {

    /**
     * Issue a signed access token for a successfully authenticated user.
     * Sets {@code tokenType=ACCESS}, includes the user's id/email/username,
     * the supplied {@code role} (e.g. {@code "MEMBER"} or {@code "ADMIN"}),
     * and a fresh {@code jti} that may later be revoked via {@link ITokenBlacklist}.
     *
     * <p>The role is passed in by the application layer (rather than read off
     * the {@code User} aggregate) so that authentication state and authorisation
     * tier never need to live on the domain object.
     */
    String issueAccessToken(User user, String role);

    /**
     * Issue a short-lived guest token (anonymous browse). Sets {@code tokenType=GUEST}
     * and {@code role=GUEST}. Useful so the same auth filter handles both
     * anonymous and authenticated requests.
     */
    String issueGuestToken();

    /**
     * Parse and cryptographically verify the token.
     * Returns {@link Optional#empty()} when the token is malformed, has a bad
     * signature, is expired, or has been revoked through {@link ITokenBlacklist}.
     */
    Optional<TokenClaims> parseAndVerify(String rawToken);

    /** Default access-token lifetime in seconds (handy for AuthResponse). */
    long getAccessTokenTtlSeconds();
}
