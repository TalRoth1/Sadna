package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.ITokenBlacklist;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory blacklist keyed by JWT id ({@code jti}).
 * Suitable for a single-process app or for tests; back this with Redis (or
 * any shared cache) once the system runs across multiple nodes.
 *
 * <p>Entries are evicted lazily on every {@code isRevoked}/{@code revoke}
 * call so the map size stays roughly bounded by the number of un-expired
 * revoked tokens.
 */
public class InMemoryTokenBlacklist implements ITokenBlacklist {

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    @Override
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            return;
        }
        purgeExpired();
        revoked.put(jti, expiresAt);
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null) return false;
        purgeExpired();
        Instant exp = revoked.get(jti);
        if (exp == null) return false;
        if (exp.isBefore(Instant.now())) {
            revoked.remove(jti);
            return false;
        }
        return true;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Instant>> it = revoked.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isBefore(now)) {
                it.remove();
            }
        }
    }
}
