package org.example.ApplicationLayer;

/**
 * Thrown by {@link ILoginRateLimiter#checkAllowed(String)} when an account
 * has accumulated too many failed login attempts within its configured
 * window — i.e. the brute-force throttle has tripped.
 *
 * <p>This is a {@link RuntimeException} so that it propagates out of an
 * existing login call chain without forcing every intermediate method
 * signature to declare it. The web layer should translate it into HTTP
 * 429 (Too Many Requests).
 *
 * <p>Carrying no PII or per-account state in the exception message is
 * deliberate — log it generically and let the client retry later.
 */
public class RateLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RateLimitExceededException(String message) {
        super(message);
    }

    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
