package org.example.ApplicationLayer.UserDTOs;

public class LoginRequest {
    public String email;
    public String plainPassword;

    // בנאי ריק חובה עבור סריאליזציה ב-Spring Boot
    public LoginRequest() {}

    public LoginRequest(String email, String plainPassword) {
        this.email = email;
        this.plainPassword = plainPassword;
    }
}