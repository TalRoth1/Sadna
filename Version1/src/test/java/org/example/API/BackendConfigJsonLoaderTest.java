package org.example.API;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import com.fasterxml.jackson.databind.ObjectMapper;

class BackendConfigJsonLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void whenConfigFileDoesNotExist_thenNoBackendConfigPropertySourceIsAdded() {
        ConfigurableEnvironment environment = new StandardEnvironment();

        BackendConfigJsonLoader loader = new BackendConfigJsonLoader(
                new ObjectMapper(),
                tempDir.resolve("backend-config.json"),
                ignored -> new FileSystemResource(tempDir.resolve("classpath-backend-config.json"))
        );

        assertDoesNotThrow(() ->
                loader.postProcessEnvironment(environment, new SpringApplication())
        );

        assertNull(environment.getPropertySources().get("backendConfigJson"));
        assertNull(environment.getProperty("backend.jwt.secret"));
    }

    @Test
    void whenConfigFileHasInvalidJsonSyntax_thenNoBackendConfigPropertySourceIsAdded() throws Exception {
        Path invalidConfig = tempDir.resolve("backend-config.json");

        Files.writeString(
                invalidConfig,
                "{ \"jwt\": { \"secret\": \"broken\", }"
        );

        ConfigurableEnvironment environment = new StandardEnvironment();

        BackendConfigJsonLoader loader = new BackendConfigJsonLoader(
                new ObjectMapper(),
                invalidConfig,
                ignored -> new FileSystemResource(tempDir.resolve("classpath-backend-config.json"))
        );

        assertDoesNotThrow(() ->
                loader.postProcessEnvironment(environment, new SpringApplication())
        );

        assertNull(environment.getPropertySources().get("backendConfigJson"));
        assertNull(environment.getProperty("backend.jwt.secret"));
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

        ConfigurableEnvironment environment = new StandardEnvironment();

        BackendConfigJsonLoader loader = new BackendConfigJsonLoader(
                new ObjectMapper(),
                validConfig,
                ignored -> new FileSystemResource(tempDir.resolve("classpath-backend-config.json"))
        );

        loader.postProcessEnvironment(environment, new SpringApplication());

        assertNotNull(environment.getPropertySources().get("backendConfigJson"));
        assertEquals("test-secret", environment.getProperty("backend.jwt.secret"));
        assertEquals("12345", environment.getProperty("backend.jwt.expirationMs"));
        assertEquals("http://localhost:*", environment.getProperty("backend.cors.allowedOriginPatterns[0]"));
        assertEquals("true", environment.getProperty("backend.cors.allowCredentials"));
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

    @Test
    void whenOverrideConfigFileIsValid_thenOverrideFileIsLoadedBeforeWorkingDirectoryFile() throws Exception {
        Path workingDirConfig = tempDir.resolve("backend-config.json");
        Path overrideConfig = tempDir.resolve("override-backend-config.json");

        Files.writeString(
                workingDirConfig,
                """
                { "jwt": { "secret": "working-dir-secret" } }
                """
        );

        Files.writeString(
                overrideConfig,
                """
                { "jwt": { "secret": "override-secret" } }
                """
        );

        ConfigurableEnvironment environment = new StandardEnvironment();

        environment.getPropertySources().addFirst(
                new MapPropertySource(
                        "testOverride",
                        Map.of("backend.config.file", overrideConfig.toString())
                )
        );

        BackendConfigJsonLoader loader = new BackendConfigJsonLoader(
                new ObjectMapper(),
                workingDirConfig,
                ignored -> new FileSystemResource(tempDir.resolve("classpath-backend-config.json"))
        );

        loader.postProcessEnvironment(environment, new SpringApplication());

        assertEquals("override-secret", environment.getProperty("backend.jwt.secret"));
    }
}