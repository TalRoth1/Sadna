package org.example.API;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "backend")
public class BackendConfigProperties {

    private final Jwt jwt = new Jwt();
    private final Profiles profiles = new Profiles();
    private final LoginRateLimiter loginRateLimiter = new LoginRateLimiter();
    private final Notifications notifications = new Notifications();
    private final ActivePurchaseCleaner activePurchaseCleaner = new ActivePurchaseCleaner();
    private final SystemMetrics systemMetrics = new SystemMetrics();
    private final DevSeed devSeed = new DevSeed();
    private final JwtAuth jwtAuth = new JwtAuth();
    private final Cors cors = new Cors();
    private final ActivePurchase activePurchase = new ActivePurchase();

    public Jwt getJwt() {
        return jwt;
    }

    public Profiles getProfiles() {
        return profiles;
    }

    public LoginRateLimiter getLoginRateLimiter() {
        return loginRateLimiter;
    }

    public Notifications getNotifications() {
        return notifications;
    }

    public ActivePurchaseCleaner getActivePurchaseCleaner() {
        return activePurchaseCleaner;
    }

    public SystemMetrics getSystemMetrics() {
        return systemMetrics;
    }

    public DevSeed getDevSeed() {
        return devSeed;
    }

    public JwtAuth getJwtAuth() {
        return jwtAuth;
    }

    public Cors getCors() {
        return cors;
    }

    public ActivePurchase getActivePurchase() {
        return activePurchase;
    }

    public static class Jwt {
        private String secret = "change-me-please-this-is-a-development-only-default-secret-key-1234567890";
        private long expirationMs = 3600000L;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMs() {
            return expirationMs;
        }

        public void setExpirationMs(long expirationMs) {
            this.expirationMs = expirationMs;
        }
    }

    public static class Profiles {
        private String active = "dev";

        public String getActive() {
            return active;
        }

        public void setActive(String active) {
            this.active = active;
        }
    }

    public static class LoginRateLimiter {
        private int maxFailedAttempts = 5;
        private Duration window = Duration.ofMinutes(15);

        public int getMaxFailedAttempts() {
            return maxFailedAttempts;
        }

        public void setMaxFailedAttempts(int maxFailedAttempts) {
            this.maxFailedAttempts = maxFailedAttempts;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    public static class Notifications {
        private Duration sseTimeout = Duration.ofMinutes(30);

        public Duration getSseTimeout() {
            return sseTimeout;
        }

        public void setSseTimeout(Duration sseTimeout) {
            this.sseTimeout = sseTimeout;
        }
    }

    public static class ActivePurchaseCleaner {
        private Duration sweepInterval = Duration.ofSeconds(1);
        private long warningBeforeExpirySeconds = 60L;

        public Duration getSweepInterval() {
            return sweepInterval;
        }

        public void setSweepInterval(Duration sweepInterval) {
            this.sweepInterval = sweepInterval;
        }

        public long getWarningBeforeExpirySeconds() {
            return warningBeforeExpirySeconds;
        }

        public void setWarningBeforeExpirySeconds(long warningBeforeExpirySeconds) {
            this.warningBeforeExpirySeconds = warningBeforeExpirySeconds;
        }
    }

    public static class SystemMetrics {
        private Duration window = Duration.ofSeconds(60);

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    public static class DevSeed {
        private String defaultPassword = "demo1234";

        public String getDefaultPassword() {
            return defaultPassword;
        }

        public void setDefaultPassword(String defaultPassword) {
            this.defaultPassword = defaultPassword;
        }
    }

    public static class JwtAuth {
        private String bearerPrefix = "Bearer ";
        private List<String> publicPaths = List.of(
                "/api/users/guest",
                "/api/users/login",
                "/api/users/register",
                "/api/users/logout",
                "/api/events/search",
                "/api/events/{eventId}");

        public String getBearerPrefix() {
            return bearerPrefix;
        }

        public void setBearerPrefix(String bearerPrefix) {
            this.bearerPrefix = bearerPrefix;
        }

        public List<String> getPublicPaths() {
            return publicPaths;
        }

        public void setPublicPaths(List<String> publicPaths) {
            this.publicPaths = publicPaths;
        }
    }

    public static class Cors {
        private List<String> allowedOriginPatterns = List.of("http://localhost:*", "http://127.0.0.1:*");
        private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        private List<String> allowedHeaders = List.of("*");
        private boolean allowCredentials = true;
        private long maxAgeSeconds = 3600L;

        public List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public long getMaxAgeSeconds() {
            return maxAgeSeconds;
        }

        public void setMaxAgeSeconds(long maxAgeSeconds) {
            this.maxAgeSeconds = maxAgeSeconds;
        }
    }

    public static class ActivePurchase {
        private int timeoutMinutes = 10;
        private float defaultMaxWaitTime = 10.0f;

        public int getTimeoutMinutes() {
            return timeoutMinutes;
        }

        public void setTimeoutMinutes(int timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
        }

        public float getDefaultMaxWaitTime() {
            return defaultMaxWaitTime;
        }

        public void setDefaultMaxWaitTime(float defaultMaxWaitTime) {
            this.defaultMaxWaitTime = defaultMaxWaitTime;
        }
    }
}