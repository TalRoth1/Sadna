package org.example.ApplicationLayer;

import java.time.Instant;

/**
 * Optional revocation list keyed by JWT id ({@code jti}).
 * <p>
 * Pure-stateless JWT auth has no logout — the client just discards the token.
 * For defence-in-depth (so an explicit "Logout" click really does invalidate
 * the token before it expires), {@link ITokenService#parseAndVerify} consults
 * this blacklist and rejects revoked jti's. Entries auto-expire at the token's
 * original {@code exp}, so the list stays bounded.
 */
public interface ITokenBlacklist {

    /** Mark a jti as revoked until the token's original expiry time. */
    void revoke(String jti, Instant expiresAt);

    /** {@code true} if the jti has been revoked and has not yet expired. */
    boolean isRevoked(String jti);
}
