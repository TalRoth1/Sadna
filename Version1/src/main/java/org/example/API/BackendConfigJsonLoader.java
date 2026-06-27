package org.example.API;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
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

public class BackendConfigJsonLoader implements EnvironmentPostProcessor {

    private static final String FILE_OVERRIDE_PROPERTY = "backend.config.file";
    private static final String DEFAULT_FILE_NAME = "backend-config.json";
    private static final String PROPERTY_PREFIX = "backend.";
    private static final String PROPERTY_SOURCE_NAME = "backendConfigJson";

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

        environment.getPropertySources().addLast(
                new MapPropertySource(PROPERTY_SOURCE_NAME, flattened)
        );

        logger.info("Loaded " + DEFAULT_FILE_NAME + " (" + flattened.size() + " properties) into the environment.");
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