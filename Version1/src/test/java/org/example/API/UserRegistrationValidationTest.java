package org.example.API;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.example.ApplicationLayer.dto.UserDTOs.RegisterRequest;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

/**
 * Verifies that Bean Validation annotations on RegisterRequest reject out-of-contract
 * inputs before they ever reach the service layer.
 */
public class UserRegistrationValidationTest {

    private static Validator validator;

    @BeforeClass
    public static void buildValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Set<ConstraintViolation<RegisterRequest>> validate(RegisterRequest req) {
        return validator.validate(req);
    }

    // ------------------------------------------------------------------ username

    @Test
    public void username_null_failsValidation() {
        assertFalse(validate(new RegisterRequest(null, "a@b.com", "Pass1234", 25)).isEmpty());
    }

    @Test
    public void username_blank_failsValidation() {
        assertFalse(validate(new RegisterRequest("   ", "a@b.com", "Pass1234", 25)).isEmpty());
    }

    @Test
    public void username_tooLong_failsValidation() {
        assertFalse(validate(new RegisterRequest("a".repeat(101), "a@b.com", "Pass1234", 25)).isEmpty());
    }

    @Test
    public void username_exactly100Chars_passesValidation() {
        assertTrue(validate(new RegisterRequest("a".repeat(100), "a@b.com", "Pass1234", 25)).isEmpty());
    }

    // ------------------------------------------------------------------ email

    @Test
    public void email_null_failsValidation() {
        assertFalse(validate(new RegisterRequest("JohnDoe", null, "Pass1234", 25)).isEmpty());
    }

    @Test
    public void email_blank_failsValidation() {
        assertFalse(validate(new RegisterRequest("JohnDoe", "   ", "Pass1234", 25)).isEmpty());
    }

    @Test
    public void email_invalidFormat_failsValidation() {
        assertFalse(validate(new RegisterRequest("JohnDoe", "not-an-email", "Pass1234", 25)).isEmpty());
    }

    @Test
    public void email_tooLong_failsValidation() {
        // 255 chars total: local-part@b.com > 254 chars
        String localPart = "a".repeat(248);
        assertFalse(validate(new RegisterRequest("JohnDoe", localPart + "@b.com", "Pass1234", 25)).isEmpty());
    }

    @Test
    public void email_valid_passesValidation() {
        assertTrue(validate(new RegisterRequest("JohnDoe", "john@example.com", "Pass1234", 25)).isEmpty());
    }

    // ------------------------------------------------------------------ password

    @Test
    public void password_null_failsValidation() {
        assertFalse(validate(new RegisterRequest("JohnDoe", "a@b.com", null, 25)).isEmpty());
    }

    @Test
    public void password_blank_failsValidation() {
        assertFalse(validate(new RegisterRequest("JohnDoe", "a@b.com", "   ", 25)).isEmpty());
    }

    @Test
    public void password_tooShort_failsValidation() {
        assertFalse(validate(new RegisterRequest("JohnDoe", "a@b.com", "1234567", 25)).isEmpty());
    }

    @Test
    public void password_tooLong_failsValidation() {
        assertFalse(validate(new RegisterRequest("JohnDoe", "a@b.com", "a".repeat(73), 25)).isEmpty());
    }

    @Test
    public void password_exactly8Chars_passesValidation() {
        assertTrue(validate(new RegisterRequest("JohnDoe", "a@b.com", "12345678", 25)).isEmpty());
    }

    @Test
    public void password_exactly72Chars_passesValidation() {
        assertTrue(validate(new RegisterRequest("JohnDoe", "a@b.com", "a".repeat(72), 25)).isEmpty());
    }

    // ------------------------------------------------------------------ age

    @Test
    public void age_negative_failsValidation() {
        assertFalse(validate(new RegisterRequest("JohnDoe", "a@b.com", "Pass1234", -1)).isEmpty());
    }

    @Test
    public void age_tooHigh_failsValidation() {
        assertFalse(validate(new RegisterRequest("JohnDoe", "a@b.com", "Pass1234", 121)).isEmpty());
    }

    @Test
    public void age_zero_passesValidation() {
        assertTrue(validate(new RegisterRequest("JohnDoe", "a@b.com", "Pass1234", 0)).isEmpty());
    }

    @Test
    public void age_120_passesValidation() {
        assertTrue(validate(new RegisterRequest("JohnDoe", "a@b.com", "Pass1234", 120)).isEmpty());
    }

    // ------------------------------------------------------------------ happy path

    @Test
    public void allFieldsValid_noViolations() {
        assertTrue(validate(new RegisterRequest("JohnDoe", "john@example.com", "Pass1234", 25)).isEmpty());
    }
}
