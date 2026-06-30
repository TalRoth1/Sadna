package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.IAuthenticationGateway;

/**
 * Development-only authentication gateway.
 *
 * Stores passwords as-is and uses straight string comparison — fine for the
 * local dev loop where we just want the system to boot and let the Event
 * Search flow be exercised end-to-end. Replace with BCrypt / Argon2 + a
 * real email validator before going anywhere near production.
 */
public class PlainTextAuthenticationGateway implements IAuthenticationGateway {

    @Override
    public String hashPassword(String plainPassword) {
        return plainPassword;
    }

    @Override
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        return plainPassword.equals(hashedPassword);
    }

    @Override
    public boolean verifyEmail(String email) {
        return email != null && email.contains("@");
    }

    @Override
    public boolean verifyPassword(String pass) {
        return pass != null && pass.length() >= 4 && pass.length() <= 72;
    }

    @Override
    public boolean verifyUserDetails(String email, String password, int age, String username) {
        return verifyEmail(email)
                && verifyPassword(password)
                && age >= 0
                && age <= 120
                && username != null
                && !username.isBlank();
    }
}
