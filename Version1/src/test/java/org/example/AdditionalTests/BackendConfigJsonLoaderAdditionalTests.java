package org.example.AdditionalTests;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.example.API.BackendConfigJsonLoader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.Assert.*;

public class BackendConfigJsonLoaderAdditionalTests {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final String VALID_JSON =
            "{\n" +
            "  \"jwt\": { \"secret\": \"test-secret-key\", \"expirationMs\": 3600000 },\n" +
            "  \"profiles\": { \"active\": \"dev\" },\n" +
            "  \"loginRateLimiter\": { \"maxFailedAttempts\": 5, \"windowMinutes\": 15 },\n" +
            "  \"notifications\": { \"sseTimeoutMs\": 1800000 },\n" +
            "  \"activePurchaseCleaner\": { \"sweepIntervalMs\": 5000, \"warningBeforeExpirySeconds\": 60 },\n" +
            "  \"lotteryScheduler\": { \"sweepIntervalMs\": 30000 },\n" +
            "  \"systemMetrics\": { \"windowSeconds\": 60 },\n" +
            "  \"devSeed\": { \"defaultPassword\": \"demo1234\" },\n" +
            "  \"jwtAuth\": { \"bearerPrefix\": \"Bearer \", \"publicPaths\": [\"/api/users/login\"] },\n" +
            "  \"cors\": {\n" +
            "    \"allowedOriginPatterns\": [\"http://localhost:*\"],\n" +
            "    \"allowedMethods\": [\"GET\"],\n" +
            "    \"allowedHeaders\": [\"*\"],\n" +
            "    \"allowCredentials\": true,\n" +
            "    \"maxAgeSeconds\": 3600\n" +
            "  },\n" +
            "  \"activePurchase\": { \"timeoutMinutes\": 10, \"defaultMaxWaitTime\": 10.0 }\n" +
            "}";

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    public void validConfig_doesNotThrow() throws IOException {
        Path config = writeTemp(VALID_JSON);
        BackendConfigJsonLoader loader = new BackendConfigJsonLoader();
        StandardEnvironment env = envWith(config);

        loader.postProcessEnvironment(env, null);

        assertNotNull(env.getPropertySources().get("backendConfigJson"));
    }

    @Test
    public void validConfig_propertiesFlattenedCorrectly() throws IOException {
        Path config = writeTemp(VALID_JSON);
        BackendConfigJsonLoader loader = new BackendConfigJsonLoader();
        StandardEnvironment env = envWith(config);

        loader.postProcessEnvironment(env, null);

        assertEquals("test-secret-key", env.getProperty("backend.jwt.secret"));
        assertEquals(Boolean.TRUE, env.getProperty("backend.cors.allowCredentials", Boolean.class));
        assertEquals("/api/users/login", env.getProperty("backend.jwtAuth.publicPaths[0]"));
    }

    // -------------------------------------------------------------------------
    // Error: file not found
    // -------------------------------------------------------------------------

    @Test
    public void fileNotFound_throwsIllegalStateException() {
        String missingPath = "/this/path/does/not/exist/backend-config.json";
        StandardEnvironment env = envWithPath(missingPath);
        BackendConfigJsonLoader loader = new BackendConfigJsonLoader();

        try {
            loader.postProcessEnvironment(env, null);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(
                    "Exception message should contain the bad path",
                    e.getMessage().contains(missingPath));
        }
    }

    // -------------------------------------------------------------------------
    // Error: malformed JSON
    // -------------------------------------------------------------------------

    @Test(expected = IllegalStateException.class)
    public void malformedJson_throwsIllegalStateException() throws IOException {
        Path config = writeTemp("{ this is not : valid json }");
        new BackendConfigJsonLoader().postProcessEnvironment(envWith(config), null);
    }

    // -------------------------------------------------------------------------
    // Error: unknown config key names (typos / misspellings)
    // -------------------------------------------------------------------------

    @Test
    public void unknownTopLevelKey_throwsWithKeyName() throws IOException {
        Path config = writeTemp("{ \"unknownSection\": {} }");
        BackendConfigJsonLoader loader = new BackendConfigJsonLoader();

        try {
            loader.postProcessEnvironment(envWith(config), null);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(
                    "Exception message should name the unknown key",
                    e.getMessage().contains("unknownSection"));
        }
    }

    @Test
    public void unknownNestedKey_throwsWithKeyName() throws IOException {
        Path config = writeTemp("{ \"jwt\": { \"secretXYZ\": \"typo\" } }");
        BackendConfigJsonLoader loader = new BackendConfigJsonLoader();

        try {
            loader.postProcessEnvironment(envWith(config), null);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(
                    "Exception message should name the unknown nested key",
                    e.getMessage().contains("secretXYZ"));
        }
    }

    // -------------------------------------------------------------------------
    // Error: invalid enum values for constrained fields
    // -------------------------------------------------------------------------

    @Test(expected = IllegalStateException.class)
    public void invalidTicketingProvider_throwsIllegalStateException() throws IOException {
        Path config = writeTemp("{ \"ticketing\": { \"defaultProvider\": \"FAKE\" } }");
        new BackendConfigJsonLoader().postProcessEnvironment(envWith(config), null);
    }

    @Test(expected = IllegalStateException.class)
    public void invalidPaymentProvider_throwsIllegalStateException() throws IOException {
        Path config = writeTemp("{ \"payment\": { \"defaultProvider\": \"FAKE\" } }");
        new BackendConfigJsonLoader().postProcessEnvironment(envWith(config), null);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Path writeTemp(String json) throws IOException {
        File file = tempFolder.newFile("config.json");
        Files.writeString(file.toPath(), json);
        return file.toPath();
    }

    private StandardEnvironment envWith(Path file) {
        return envWithPath(file.toString());
    }

    private StandardEnvironment envWithPath(String path) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(
                new MapPropertySource("test-override", Map.of("backend.config.file", path)));
        return env;
    }
}
