package org.example.ApplicationLayer.dto.UserDTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(max = 100, message = "Username must be 100 characters or fewer")
    public String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 254, message = "Email must be 254 characters or fewer")
    public String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    public String plainPassword;

    @Min(value = 0, message = "Age must be 0 or greater")
    @Max(value = 120, message = "Age must be 120 or less")
    public int age;

    public RegisterRequest() {}

    public RegisterRequest(String username, String email, String plainPassword, int age) {
        this.username = username;
        this.email = email;
        this.plainPassword = plainPassword;
        this.age = age;
    }
}
