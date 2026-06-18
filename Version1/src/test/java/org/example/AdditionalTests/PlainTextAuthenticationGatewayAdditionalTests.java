package org.example.AdditionalTests;

import org.example.InfrastructureLayer.PlainTextAuthenticationGateway;
import org.junit.Test;

import static org.junit.Assert.*;

public class PlainTextAuthenticationGatewayAdditionalTests {

    @Test
    public void hashAndVerifyPassword_coverPlainTextBranches() {
        PlainTextAuthenticationGateway gateway = new PlainTextAuthenticationGateway();

        assertEquals("secret", gateway.hashPassword("secret"));
        assertNull(gateway.hashPassword(null));

        assertTrue(gateway.verifyPassword("secret", "secret"));
        assertFalse(gateway.verifyPassword("secret", "other"));
        assertFalse(gateway.verifyPassword(null, "secret"));
        assertFalse(gateway.verifyPassword("secret", null));
    }

    @Test
    public void verifyEmailPasswordAndUserDetails_coverValidationBranches() {
        PlainTextAuthenticationGateway gateway = new PlainTextAuthenticationGateway();

        assertTrue(gateway.verifyEmail("a@b"));
        assertFalse(gateway.verifyEmail(null));
        assertFalse(gateway.verifyEmail("no-at-sign"));

        assertTrue(gateway.verifyPassword("1234"));
        assertFalse(gateway.verifyPassword(null));
        assertFalse(gateway.verifyPassword("123"));

        assertTrue(gateway.verifyUserDetails("a@b", "1234", 0, "alice"));
        assertFalse(gateway.verifyUserDetails("ab", "1234", 20, "alice"));
        assertFalse(gateway.verifyUserDetails("a@b", "123", 20, "alice"));
        assertFalse(gateway.verifyUserDetails("a@b", "1234", -1, "alice"));
        assertFalse(gateway.verifyUserDetails("a@b", "1234", 20, null));
        assertFalse(gateway.verifyUserDetails("a@b", "1234", 20, "   "));
    }
}
