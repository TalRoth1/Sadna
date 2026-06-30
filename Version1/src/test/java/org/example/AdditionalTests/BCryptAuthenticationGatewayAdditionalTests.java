package org.example.AdditionalTests;

import org.example.InfrastructureLayer.BCryptAuthenticationGateway;
import org.junit.Test;

import static org.junit.Assert.*;

public class BCryptAuthenticationGatewayAdditionalTests {

    @Test
    public void hashPassword_rejectsMissingPasswordsAndProducesBCryptHash() {
        BCryptAuthenticationGateway gateway = new BCryptAuthenticationGateway();

        assertThrows(IllegalArgumentException.class, () -> gateway.hashPassword(null));
        assertThrows(IllegalArgumentException.class, () -> gateway.hashPassword("   "));

        String hash = gateway.hashPassword("CorrectHorseBatteryStaple");

        assertNotNull(hash);
        assertTrue(hash.matches("^\\$2[aby]\\$\\d\\d\\$.{53}$"));
        assertNotEquals("CorrectHorseBatteryStaple", hash);
    }

    @Test
    public void verifyPassword_twoArgumentVersionCoversValidInvalidAndMalformedHashes() {
        BCryptAuthenticationGateway gateway = new BCryptAuthenticationGateway();
        String hash = gateway.hashPassword("Secret123");

        assertTrue(gateway.verifyPassword("Secret123", hash));
        assertFalse(gateway.verifyPassword("WrongSecret", hash));
        assertFalse(gateway.verifyPassword(null, hash));
        assertFalse(gateway.verifyPassword("Secret123", null));
        assertFalse(gateway.verifyPassword("Secret123", "   "));
        assertFalse(gateway.verifyPassword("Secret123", "not-a-bcrypt-hash"));
    }

    @Test
    public void verifyEmailPasswordAndUserDetails_coverAllValidationBranches() {
        BCryptAuthenticationGateway gateway = new BCryptAuthenticationGateway();

        assertTrue(gateway.verifyEmail("user@example.com"));
        assertFalse(gateway.verifyEmail(null));
        assertFalse(gateway.verifyEmail("missing-at.example.com"));
        assertFalse(gateway.verifyEmail("user@"));
        assertFalse(gateway.verifyEmail("user@example"));
        assertFalse(gateway.verifyEmail("user name@example.com"));

        assertTrue(gateway.verifyPassword("12345678"));
        assertFalse(gateway.verifyPassword(null));
        assertFalse(gateway.verifyPassword("1234567"));

        assertTrue(gateway.verifyUserDetails("user@example.com", "12345678", 0, "alice"));
        assertFalse(gateway.verifyUserDetails("bad-email", "12345678", 30, "alice"));
        assertFalse(gateway.verifyUserDetails("user@example.com", "short", 30, "alice"));
        assertFalse(gateway.verifyUserDetails("user@example.com", "12345678", -1, "alice"));
        assertFalse(gateway.verifyUserDetails("user@example.com", "12345678", 30, null));
        assertFalse(gateway.verifyUserDetails("user@example.com", "12345678", 30, "   "));
    }

    @Test
    public void verifyPassword_singleArg_rejectsPasswordsOver72Characters() {
        BCryptAuthenticationGateway gateway = new BCryptAuthenticationGateway();
        String exactly72 = "a".repeat(72);
        String exactly73 = "a".repeat(73);

        assertTrue("72-char password should be accepted", gateway.verifyPassword(exactly72));
        assertFalse("73-char password must be rejected to prevent silent BCrypt truncation",
                gateway.verifyPassword(exactly73));
    }

    @Test
    public void verifyUserDetails_rejectsPasswordOver72Characters() {
        BCryptAuthenticationGateway gateway = new BCryptAuthenticationGateway();
        assertFalse(gateway.verifyUserDetails("user@example.com", "a".repeat(73), 25, "alice"));
    }
}
