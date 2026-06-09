package org.example.InfrastructureLayer.Persistence;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GeneratePasswordHash
{
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

        String password = "12345678";
        String hash = encoder.encode(password);

        System.out.println(hash);
        System.out.println(encoder.matches(password, hash));
    }
}
