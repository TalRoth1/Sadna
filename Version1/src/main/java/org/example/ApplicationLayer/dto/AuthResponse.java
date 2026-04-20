package org.example.ApplicationLayer.dto;

public class AuthResponse {
    public boolean isSuccess;
    public String message;
    public String userId; // רלוונטי להתחברות והרשמה

    public AuthResponse(){

    }
    public AuthResponse(boolean b, String message, String memberId) {
        this.isSuccess = b;
        this.message = message;
        this.userId = memberId;
    }
}
