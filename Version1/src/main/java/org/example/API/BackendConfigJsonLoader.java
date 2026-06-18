package org.example.API;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
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
            logger.log(Level.WARNING,
                    "Failed to read " + DEFAULT_FILE_NAME + "; falling back to inline defaults: " + e.getMessage(), e);
            return;
        }

        if (root == null) {
            logger.warning(DEFAULT_FILE_NAME + " not found on filesystem or classpath; "
                    + "backend.* config falls back to inline defaults.");
            return;
        }

        Map<String, Object> flattened = new LinkedHashMap<>();
        flatten("", root, flattened);

        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, flattened));
        logger.info("Loaded " + DEFAULT_FILE_NAME + " (" + flattened.size() + " properties) into the environment.");
    }

    /**
     * Locates and parses the config file. First match wins: the
     * {@value #FILE_OVERRIDE_PROPERTY} system property, then the working-directory
     * file (where the file actually lives), then a classpath resource.
     *
     * @return the parsed JSON root, or {@code null} if no file was found.
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
            logger.warning(FILE_OVERRIDE_PROPERTY + " set to '" + override + "' but that file is not readable.");
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

        return null;
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
}
