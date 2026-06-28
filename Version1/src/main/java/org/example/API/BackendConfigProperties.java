package org.example.API;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "backend")
public class BackendConfigProperties {

    private final Jwt jwt = new Jwt();
    private final Profiles profiles = new Profiles();
    private final Database database = new Database();
    private final LoginRateLimiter loginRateLimiter = new LoginRateLimiter();
    private final Notifications notifications = new Notifications();
    private final ActivePurchaseCleaner activePurchaseCleaner = new ActivePurchaseCleaner();
    private final LotteryScheduler lotteryScheduler = new LotteryScheduler();
    private final SystemMetrics systemMetrics = new SystemMetrics();
    private final DevSeed devSeed = new DevSeed();
    private final JwtAuth jwtAuth = new JwtAuth();
    private final Cors cors = new Cors();
    private final ActivePurchase activePurchase = new ActivePurchase();
    private final Ticketing ticketing = new Ticketing();
    private final Payment payment = new Payment();

    public Jwt getJwt() {
        return jwt;
    }

    public Profiles getProfiles() {
        return profiles;
    }

    public Database getDatabase() {
        return database;
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

    public LotteryScheduler getLotteryScheduler() {
        return lotteryScheduler;
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

    public Ticketing getTicketing() {
        return ticketing;
    }

    public Payment getPayment() {
        return payment;
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

    public static class Database {
        private String mode = "in-memory";
        private final Gcp gcp = new Gcp();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public Gcp getGcp() {
            return gcp;
        }
    }

    public static class Gcp {
        private String url = "jdbc:postgresql://35.242.177.151:5432/postgres";
        private String username = "postgres";
        private String password = "123456";
        private String driverClassName = "org.postgresql.Driver";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
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
        private long sweepIntervalMs = 1000L;
        private long warningBeforeExpirySeconds = 60L;

        public Duration getSweepInterval() {
            return Duration.ofMillis(sweepIntervalMs);
        }

        public void setSweepIntervalMs(long sweepIntervalMs) {
            this.sweepIntervalMs = sweepIntervalMs;
        }

        public long getWarningBeforeExpirySeconds() {
            return warningBeforeExpirySeconds;
        }

        public void setWarningBeforeExpirySeconds(long warningBeforeExpirySeconds) {
            this.warningBeforeExpirySeconds = warningBeforeExpirySeconds;
        }
    }

    public static class LotteryScheduler {
        private long sweepIntervalMs = 30000L;

        public Duration getSweepInterval() {
            return Duration.ofMillis(sweepIntervalMs);
        }

        public void setSweepIntervalMs(long sweepIntervalMs) {
            this.sweepIntervalMs = sweepIntervalMs;
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

    /**
     * Ticket-issuance integration settings. {@code defaultProvider} selects
     * which registered {@link org.example.ApplicationLayer.TicketingProvider}
     * the {@code DelegatingTicketingGateway} routes to (e.g. {@code EXTERNAL}
     * for the real supply system, {@code SIMULATED} for dev / tests). Kept in
     * configuration so switching providers needs no code change.
     */
    public static class Ticketing {
        private String defaultProvider = "EXTERNAL";
        private String serviceUrl = "https://damp-lynna-wsep-1984852e.koyeb.app/";

        public String getDefaultProvider() {
            return defaultProvider;
        }

        public void setDefaultProvider(String defaultProvider) {
            this.defaultProvider = defaultProvider;
        }

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }
    }

    /**
     * Payment-clearing integration settings (general requirement I.3).
     * {@code defaultProvider} selects which registered
     * {@link org.example.ApplicationLayer.PaymentProvider} the
     * {@code DelegatingPaymentGateway} routes to ({@code EXTERNAL} for the
     * real clearing system, {@code SIMULATED} for dev / tests).
     * {@code serviceUrl} is the external clearing system base URL, kept in
     * configuration (Version 3) rather than hard-coded in the adapter.
     */
    public static class Payment {
        private String defaultProvider = "EXTERNAL";
        private String serviceUrl = "https://damp-lynna-wsep-1984852e.koyeb.app/";

        public String getDefaultProvider() {
            return defaultProvider;
        }

        public void setDefaultProvider(String defaultProvider) {
            this.defaultProvider = defaultProvider;
        }

        public String getServiceUrl() {
            return serviceUrl;
        }

        public void setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl;
        }
    }
}