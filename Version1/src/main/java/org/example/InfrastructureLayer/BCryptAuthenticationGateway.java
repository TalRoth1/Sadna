package org.example.InfrastructureLayer;

import org.example.ApplicationLayer.IAuthenticationGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Profile("localdb")
public class BCryptAuthenticationGateway implements IAuthenticationGateway {

    private static final int STRENGTH = 12;
    private static final String BCRYPT_REGEX =
            "^\\$2[aby]\\$\\d\\d\\$.{53}$";

    private final BCryptPasswordEncoder encoder =
            new BCryptPasswordEncoder(STRENGTH);

    @Override
    public String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        return encoder.encode(plainPassword);
    }

    @Override
    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null || hashedPassword.isBlank()) {
            return false;
        }

        if (!isBCryptHash(hashedPassword)) {
            return false;
        }

        return encoder.matches(plainPassword, hashedPassword);
    }

    @Override
    public boolean verifyEmail(String email) {
        return email != null
                && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    @Override
    public boolean verifyPassword(String pass) {
        return pass != null && pass.length() >= 8 && pass.length() <= 72;
    }

    @Override
    public boolean verifyUserDetails(String email, String password, float age, String username) {
        return verifyEmail(email)
                && verifyPassword(password)
                && age >= 0
                && age <= 120
                && username != null
                && !username.isBlank();
    }

    private boolean isBCryptHash(String value) {
        return value.matches(BCRYPT_REGEX);
    }
}