package org.example.API;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Loads {@code backend-config.json} into the Spring {@link ConfigurableEnvironment}
 * so it drives {@code @ConfigurationProperties(prefix = "backend")}
 * ({@link BackendConfigProperties}).
 *
 * <p>The JSON tree is flattened into dotted, {@code backend.}-prefixed property
 * names (arrays become indexed {@code key[i]} entries) and registered as a
 * {@link MapPropertySource}. It is added with {@code addLast} so explicit
 * {@code application*.properties} and command-line args stay authoritative,
 * while the JSON still overrides the inline Java defaults in
 * {@link BackendConfigProperties}.
 *
 * <p>Startup is aborted (with a SEVERE log) if the config file is missing,
 * contains an unrecognized property name, or contains an invalid value for a
 * constrained field (e.g. an unknown provider name).
 *
 * <p>{@link EnvironmentPostProcessor}s run before the application context is
 * created, so this class is registered via {@code META-INF/spring.factories}
 * rather than component scanning.
 *
 * <p>This processor runs <em>just before</em>
 * {@link ConfigDataEnvironmentPostProcessor} (see {@link #getOrder()}). That
 * ordering is essential: it sets {@code spring.profiles.active} (derived from
 * {@code backend.database.mode}) into the environment <em>before</em> config
 * data is loaded, so Spring Boot then imports the matching
 * {@code application-<profile>.properties} file itself. If we instead flipped
 * the profile <em>after</em> config data had run, the wrong profile's property
 * file (e.g. the localdb datasource) would already be loaded and the right
 * one (e.g. the dev JPA-autoconfig excludes) never would be.
 */
public class BackendConfigJsonLoader implements EnvironmentPostProcessor, Ordered {

    // Run immediately before Spring Boot loads application*.properties so the
    // profile we select drives which profile-specific property file is imported.
    private static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER - 1;

    private static final String FILE_OVERRIDE_PROPERTY = "backend.config.file";
    private static final String DEFAULT_FILE_NAME = "backend-config.json";
    private static final String PROPERTY_PREFIX = "backend.";
    private static final String PROPERTY_SOURCE_NAME = "backendConfigJson";
    private static final String RUNTIME_OVERRIDE_SOURCE_NAME = "backendConfigRuntimeOverrides";

    private static final Logger logger = Logger.getLogger(BackendConfigJsonLoader.class.getName());

    private final ObjectMapper objectMapper;
    private final Path workingDirectoryConfigFile;
    private final Function<String, Resource> classpathResourceFactory;

    public BackendConfigJsonLoader() {
        this(new ObjectMapper(), Path.of(DEFAULT_FILE_NAME), ClassPathResource::new);
    }

    BackendConfigJsonLoader(
            ObjectMapper objectMapper,
            Path workingDirectoryConfigFile,
            Function<String, Resource> classpathResourceFactory
    ) {
        this.objectMapper = objectMapper;
        this.workingDirectoryConfigFile = workingDirectoryConfigFile;
        this.classpathResourceFactory = classpathResourceFactory;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        JsonNode root = readConfig(environment);

        validateShape(root);

        Map<String, Object> flattened = new LinkedHashMap<>();
        flatten("", root, flattened);

        Map<String, Object> runtimeOverrides = buildRuntimeOverrides(flattened);
        if (!runtimeOverrides.isEmpty()) {
            // addFirst so this beats the default spring.profiles.active=localdb in
            // application.properties. Because this processor runs before
            // ConfigDataEnvironmentPostProcessor (see getOrder()), config data then
            // resolves the active profile from here and imports the matching
            // application-<profile>.properties file.
            environment.getPropertySources().addFirst(new MapPropertySource(RUNTIME_OVERRIDE_SOURCE_NAME, runtimeOverrides));
            logger.info("Applied " + runtimeOverrides.size()
                    + " runtime override(s) from backend-config.json (profile/datasource).");
        }

        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, flattened));
        logger.info("Loaded " + DEFAULT_FILE_NAME + " (" + flattened.size() + " properties) into the environment.");
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * Validates the parsed JSON against {@link ConfigShape}: unknown property names
     * and invalid enum values both abort startup with a SEVERE log entry.
     */
    private void validateShape(JsonNode root) {
        ObjectMapper strict = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        try {
            strict.treeToValue(root, ConfigShape.class);
        } catch (UnrecognizedPropertyException e) {
            String msg = "Unknown property in " + DEFAULT_FILE_NAME + ": '"
                    + e.getPropertyName() + "' at " + e.getPathReference()
                    + " — check for typos in the config file.";
            logger.severe(msg);
            throw new IllegalStateException(msg, e);
        } catch (JsonProcessingException e) {
            String msg = "Invalid value in " + DEFAULT_FILE_NAME + ": " + e.getOriginalMessage();
            logger.severe(msg);
            throw new IllegalStateException(msg, e);
        }
    }

    private Map<String, Object> buildRuntimeOverrides(Map<String, Object> flattened) {
        Map<String, Object> overrides = new LinkedHashMap<>();

        String mode = asString(flattened.get("backend.database.mode"));
        if (mode != null) {
            String normalizedMode = mode.trim().toLowerCase(Locale.ROOT);
            if (isInMemoryMode(normalizedMode)) {
                // "dev" profile uses the in-memory repositories in this codebase.
                overrides.put("spring.profiles.active", "dev");
            } else if (isGcpMode(normalizedMode)) {
                // "localdb" profile is the JPA/PostgreSQL-backed profile.
                overrides.put("spring.profiles.active", "localdb");
                putIfPresent(overrides, "spring.datasource.url", flattened.get("backend.database.gcp.url"));
                putIfPresent(overrides, "spring.datasource.username", flattened.get("backend.database.gcp.username"));
                putIfPresent(overrides, "spring.datasource.password", flattened.get("backend.database.gcp.password"));
                putIfPresent(overrides, "spring.datasource.driver-class-name",
                        flattened.get("backend.database.gcp.driverClassName"));
            } else {
                logger.warning("Unsupported backend.database.mode='" + mode
                        + "'. Use in-memory or gcp. Keeping existing Spring profile settings.");
            }
        } else {
            // Backward-compatible fallback: respect explicit backend.profiles.active.
            putIfPresent(overrides, "spring.profiles.active", flattened.get("backend.profiles.active"));
        }

        return overrides;
    }

    private boolean isInMemoryMode(String mode) {
        return "in-memory".equals(mode)
                || "inmemory".equals(mode)
                || "memory".equals(mode)
                || "dev".equals(mode);
    }

    private boolean isGcpMode(String mode) {
        return "gcp".equals(mode)
                || "google-cloud".equals(mode)
                || "google_cloud".equals(mode)
                || "cloudsql".equals(mode)
                || "cloud-sql".equals(mode)
                || "localdb".equals(mode)
                || "postgres".equals(mode);
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = asString(value);
        if (text == null || text.isBlank()) {
            return;
        }
        target.put(key, text);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    /**
     * Locates and parses the config file. First match wins: the
     * {@value #FILE_OVERRIDE_PROPERTY} system property, then the working-directory
     * file, then a classpath resource. Throws {@link IllegalStateException} if no
     * file is found.
     * file (where the file actually lives).
     */
    private JsonNode readConfig(ConfigurableEnvironment environment) {
        String override = environment.getProperty(FILE_OVERRIDE_PROPERTY);

        if (override != null && !override.isBlank()) {
            Path path = Path.of(override);
            if (!Files.isReadable(path)) {
                String message = FILE_OVERRIDE_PROPERTY + " is set to '" + override
                        + "' but the file is not readable. The backend configuration location changed or is invalid.";
                logger.severe(message);
                throw new IllegalStateException(message);
            }

            return parseJson(path);
        }

        Path workingDirFile = Path.of(DEFAULT_FILE_NAME);
        if (!Files.isReadable(workingDirFile)) {
            String message = "Required config file '" + workingDirFile.toAbsolutePath()
                    + "' is missing or not readable. If you moved it, set -D" + FILE_OVERRIDE_PROPERTY
                    + "=/absolute/path/to/" + DEFAULT_FILE_NAME + ".";
            logger.severe(message);
            throw new IllegalStateException(message);
        }

        return parseJson(workingDirFile);
    }

    private JsonNode parseJson(Path filePath) {
        try (InputStream in = Files.newInputStream(filePath)) {
            return objectMapper.readTree(in);
        } catch (JsonProcessingException e) {
            String message = "Invalid JSON syntax in backend config file '"
                    + filePath.toAbsolutePath() + "': " + e.getOriginalMessage();
            logger.log(Level.SEVERE, message, e);
            throw new IllegalStateException(message, e);
        } catch (IOException e) {
            String message = "Failed to read backend config file '"
                    + filePath.toAbsolutePath() + "': " + e.getMessage();
            logger.log(Level.SEVERE, message, e);
            throw new IllegalStateException(message, e);
        }
    }

    private void flatten(String prefix, JsonNode node, Map<String, Object> target) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String key = prefix.isEmpty()
                        ? PROPERTY_PREFIX + field.getKey()
                        : prefix + "." + field.getKey();

                flatten(key, field.getValue(), target);
            }

            return;
        }

        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flatten(prefix + "[" + i + "]", node.get(i), target);
            }

            return;
        }

        target.put(prefix, scalarValue(node));
    }

    private Object scalarValue(JsonNode node) {
        if (node.isBoolean()) {
            return node.booleanValue();
        }

        if (node.isInt() || node.isLong()) {
            return node.longValue();
        }

        if (node.isNumber()) {
            return node.numberValue();
        }

        return node.asText();
    }

    // -------------------------------------------------------------------------
    // Config shape — used only by validateShape(). Field names must match the
    // exact JSON key names (which differ from the Java property names for
    // Duration fields that use aliases like windowMinutes, sseTimeoutMs, etc.).
    // -------------------------------------------------------------------------

    private static final class ConfigShape {
        public JwtShape jwt;
        public ProfilesShape profiles;
        public DatabaseShape database;
        public LoginRateLimiterShape loginRateLimiter;
        public NotificationsShape notifications;
        public ActivePurchaseCleanerShape activePurchaseCleaner;
        public LotterySchedulerShape lotteryScheduler;
        public SystemMetricsShape systemMetrics;
        public DevSeedShape devSeed;
        public JwtAuthShape jwtAuth;
        public CorsShape cors;
        public ActivePurchaseShape activePurchase;
        public TicketingShape ticketing;
        public PaymentShape payment;
        public AdminShape admin;

        static final class JwtShape {
            public String secret;
            public Long expirationMs;
        }

        static final class ProfilesShape {
            public String active;
        }

        static final class LoginRateLimiterShape {
            public Integer maxFailedAttempts;
            public Integer windowMinutes;
        }

        static final class NotificationsShape {
            public Long sseTimeoutMs;
        }

        static final class ActivePurchaseCleanerShape {
            public Long sweepIntervalMs;
            public Long warningBeforeExpirySeconds;
        }

        static final class LotterySchedulerShape {
            public Long sweepIntervalMs;
        }

        static final class SystemMetricsShape {
            public Long windowSeconds;
        }

        static final class DevSeedShape {
            public String defaultPassword;
        }

        static final class JwtAuthShape {
            public String bearerPrefix;
            public List<String> publicPaths;
        }

        static final class CorsShape {
            public List<String> allowedOriginPatterns;
            public List<String> allowedMethods;
            public List<String> allowedHeaders;
            public Boolean allowCredentials;
            public Long maxAgeSeconds;
        }

        static final class ActivePurchaseShape {
            public Integer timeoutMinutes;
            public Float defaultMaxWaitTime;
        }

        static final class DatabaseShape {
            public String mode;
            public GcpShape gcp;

            static final class GcpShape {
                public String url;
                public String username;
                public String password;
                public String driverClassName;
            }
        }

        enum Provider { EXTERNAL, SIMULATED }

        static final class TicketingShape {
            public Provider defaultProvider;
            public String serviceUrl;
        }

        static final class PaymentShape {
            public Provider defaultProvider;
            public String serviceUrl;
        }

        static final class AdminShape {
            public String id;
        }
    }
}
