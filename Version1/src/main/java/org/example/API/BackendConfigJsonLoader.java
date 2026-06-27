package org.example.API;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

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
 */
public class BackendConfigJsonLoader implements EnvironmentPostProcessor {

    /** Optional override: {@code -Dbackend.config.file=/path/to/backend-config.json}. */
    private static final String FILE_OVERRIDE_PROPERTY = "backend.config.file";
    private static final String DEFAULT_FILE_NAME = "backend-config.json";
    private static final String PROPERTY_PREFIX = "backend.";
    private static final String PROPERTY_SOURCE_NAME = "backendConfigJson";

    private static final Logger logger = Logger.getLogger(BackendConfigJsonLoader.class.getName());

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        JsonNode root;
        try {
            root = readConfig(environment);
        } catch (IOException e) {
            logger.log(Level.SEVERE,
                    "Failed to read " + DEFAULT_FILE_NAME + "; server startup aborted: " + e.getMessage(), e);
            throw new IllegalStateException(
                    "Failed to read " + DEFAULT_FILE_NAME + ": " + e.getMessage(), e);
        }

        validateShape(root);

        Map<String, Object> flattened = new LinkedHashMap<>();
        flatten("", root, flattened);

        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, flattened));
        logger.info("Loaded " + DEFAULT_FILE_NAME + " (" + flattened.size() + " properties) into the environment.");
    }

    /**
     * Locates and parses the config file. First match wins: the
     * {@value #FILE_OVERRIDE_PROPERTY} system property, then the working-directory
     * file, then a classpath resource. Throws {@link IllegalStateException} if no
     * file is found.
     */
    private JsonNode readConfig(ConfigurableEnvironment environment) throws IOException {
        String override = environment.getProperty(FILE_OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            Path path = Path.of(override);
            if (Files.isReadable(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    return objectMapper.readTree(in);
                }
            }
            String msg = FILE_OVERRIDE_PROPERTY + " set to '" + override
                    + "' but that file is not readable; server startup aborted.";
            logger.severe(msg);
            throw new IllegalStateException(msg);
        }

        Path workingDirFile = Path.of(DEFAULT_FILE_NAME);
        if (Files.isReadable(workingDirFile)) {
            try (InputStream in = Files.newInputStream(workingDirFile)) {
                return objectMapper.readTree(in);
            }
        }

        Resource classpathResource = new ClassPathResource(DEFAULT_FILE_NAME);
        if (classpathResource.exists()) {
            try (InputStream in = classpathResource.getInputStream()) {
                return objectMapper.readTree(in);
            }
        }

        String msg = DEFAULT_FILE_NAME + " not found on filesystem or classpath; server startup aborted.";
        logger.severe(msg);
        throw new IllegalStateException(msg);
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

    /**
     * Recursively flattens a JSON node into dotted property names. Objects become
     * {@code prefix.child}, arrays become {@code prefix[i]}, and scalars are stored
     * as their raw value so Spring's {@code Binder} can convert them. The root is
     * passed with an empty prefix and each top-level key is given the
     * {@value #PROPERTY_PREFIX} prefix.
     */
    private void flatten(String prefix, JsonNode node, Map<String, Object> target) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                String key = prefix.isEmpty()
                        ? PROPERTY_PREFIX + field.getKey()
                        : prefix + "." + field.getKey();
                flatten(key, field.getValue(), target);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flatten(prefix + "[" + i + "]", node.get(i), target);
            }
        } else {
            target.put(prefix, scalarValue(node));
        }
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

        enum Provider { EXTERNAL, SIMULATED }

        static final class TicketingShape {
            public Provider defaultProvider;
            public String serviceUrl;
        }

        static final class PaymentShape {
            public Provider defaultProvider;
            public String serviceUrl;
        }
    }
}
