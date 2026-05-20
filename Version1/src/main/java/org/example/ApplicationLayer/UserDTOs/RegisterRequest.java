package org.example.ApplicationLayer.UserDTOs;

public class RegisterRequest {
    public String username;
    public String email;
    public String plainPassword;
    public int age;

    // בנאי ריק חובה עבור סריאליזציה ב-Spring Boot
    public RegisterRequest() {}

    public RegisterRequest(String username, String email, String plainPassword, int age) {
        this.username = username;
        this.email = email;
        this.plainPassword = plainPassword;
        this.age = age;
    }
}