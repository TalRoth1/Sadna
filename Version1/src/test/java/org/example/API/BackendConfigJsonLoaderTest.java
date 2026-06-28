package org.example.API;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.fasterxml.jackson.databind.ObjectMapper;

class BackendConfigJsonLoaderTest {

    @TempDir
    Path tempDir;

    private ConfigurableEnvironment environmentPointingAt(Path configFile) {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource(
                        "testOverride",
                        Map.of("backend.config.file", configFile.toString())
                )
        );
        return environment;
    }

    @Test
    void whenConfigFileDoesNotExist_thenStartupIsAborted() {
        ConfigurableEnvironment environment =
                environmentPointingAt(tempDir.resolve("missing-backend-config.json"));

        assertThrows(
                IllegalStateException.class,
                () -> new BackendConfigJsonLoader().postProcessEnvironment(environment, new SpringApplication())
        );
    }

    @Test
    void whenConfigFileHasInvalidJsonSyntax_thenStartupIsAborted() throws Exception {
        Path invalidConfig = tempDir.resolve("backend-config.json");
        Files.writeString(invalidConfig, "{ \"jwt\": { \"secret\": \"broken\", }");

        ConfigurableEnvironment environment = environmentPointingAt(invalidConfig);

        assertThrows(
                IllegalStateException.class,
                () -> new BackendConfigJsonLoader().postProcessEnvironment(environment, new SpringApplication())
        );
    }

    @Test
    void whenConfigFileIsValid_thenBackendPropertiesAreLoadedIntoEnvironment() throws Exception {
        Path validConfig = tempDir.resolve("backend-config.json");
        Files.writeString(
                validConfig,
                """
                {
                  "jwt": {
                    "secret": "test-secret",
                    "expirationMs": 12345
                  },
                  "cors": {
                    "allowedOriginPatterns": ["http://localhost:*"],
                    "allowCredentials": true
                  }
                }
                """
        );

        ConfigurableEnvironment environment = environmentPointingAt(validConfig);

        new BackendConfigJsonLoader().postProcessEnvironment(environment, new SpringApplication());

        assertNotNull(environment.getPropertySources().get("backendConfigJson"));
        assertEquals("test-secret", environment.getProperty("backend.jwt.secret"));
        assertEquals("12345", environment.getProperty("backend.jwt.expirationMs"));
        assertEquals("http://localhost:*", environment.getProperty("backend.cors.allowedOriginPatterns[0]"));
        assertEquals("true", environment.getProperty("backend.cors.allowCredentials"));
    }

    @Test
    void whenOverrideConfigFileIsSet_thenOverrideFileIsLoaded() throws Exception {
        Path overrideConfig = tempDir.resolve("override-backend-config.json");
        Files.writeString(overrideConfig, "{ \"jwt\": { \"secret\": \"override-secret\" } }");

        ConfigurableEnvironment environment = environmentPointingAt(overrideConfig);

        new BackendConfigJsonLoader().postProcessEnvironment(environment, new SpringApplication());

        assertEquals("override-secret", environment.getProperty("backend.jwt.secret"));
    }

    @Test
    void realBackendConfigFileShouldExistInProjectRoot() {
        Path configPath = Path.of("backend-config.json");

        assertTrue(
                Files.exists(configPath),
                "backend-config.json must exist in the Version1 project root"
        );
    }

    @Test
    void realBackendConfigFileShouldHaveValidJsonSyntax() {
        Path configPath = Path.of("backend-config.json");

        assertTrue(
                Files.exists(configPath),
                "backend-config.json must exist in the Version1 project root"
        );

        assertDoesNotThrow(() -> {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.readTree(configPath.toFile());
        });
    }
}
