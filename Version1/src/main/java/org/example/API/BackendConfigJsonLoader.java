package org.example.API;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * <p>This processor runs <em>just before</em>
 * {@link ConfigDataEnvironmentPostProcessor} (see {@link #getOrder()}) so the
 * profile it selects (from {@code backend.database.mode}) is set before Spring
 * Boot imports the matching {@code application-<profile>.properties}.
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
        JsonNode root;

        try {
            root = readConfig(environment);
        } catch (IOException e) {
            logger.log(
                    Level.WARNING,
                    "Failed to read " + DEFAULT_FILE_NAME + "; falling back to inline defaults: " + e.getMessage(),
                    e
            );
            return;
        }

        if (root == null) {
            logger.warning(
                    DEFAULT_FILE_NAME + " not found on filesystem or classpath; "
                            + "backend.* config falls back to inline defaults."
            );
            return;
        }

        Map<String, Object> flattened = new LinkedHashMap<>();
        flatten("", root, flattened);

        Map<String, Object> runtimeOverrides = buildRuntimeOverrides(flattened);
        if (!runtimeOverrides.isEmpty()) {
            // addFirst so this beats the default spring.profiles.active in
            // application.properties; config data then resolves the active profile
            // from here and imports the matching application-<profile>.properties.
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

    private Map<String, Object> buildRuntimeOverrides(Map<String, Object> flattened) {
        Map<String, Object> overrides = new LinkedHashMap<>();

        String mode = asString(flattened.get("backend.database.mode"));
        if (mode != null) {
            String normalizedMode = mode.trim().toLowerCase(Locale.ROOT);
            if (isInMemoryMode(normalizedMode)) {
                overrides.put("spring.profiles.active", "dev");
            } else if (isGcpMode(normalizedMode)) {
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

    private JsonNode readConfig(ConfigurableEnvironment environment) throws IOException {
        String override = environment.getProperty(FILE_OVERRIDE_PROPERTY);

        if (override != null && !override.isBlank()) {
            Path path = Path.of(override);

            if (Files.isReadable(path)) {
                try (InputStream in = Files.newInputStream(path)) {
                    return objectMapper.readTree(in);
                }
            }

            logger.warning(FILE_OVERRIDE_PROPERTY + " set to '" + override + "' but that file is not readable.");
        }

        if (Files.isReadable(workingDirectoryConfigFile)) {
            try (InputStream in = Files.newInputStream(workingDirectoryConfigFile)) {
                return objectMapper.readTree(in);
            }
        }

        Resource classpathResource = classpathResourceFactory.apply(DEFAULT_FILE_NAME);

        if (classpathResource.exists()) {
            try (InputStream in = classpathResource.getInputStream()) {
                return objectMapper.readTree(in);
            }
        }

        return null;
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
}
