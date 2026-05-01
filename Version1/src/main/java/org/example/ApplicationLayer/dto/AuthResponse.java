package org.example.ApplicationLayer.dto;

import java.util.UUID;

public class AuthResponse {
    public boolean isSuccess;
    public String message;
    public UUID userId;

    public AuthResponse(){

    }
    public AuthResponse(boolean b, String message, UUID memberId) {
        this.isSuccess = b;
        this.message = message;
        this.userId = memberId;
    }
}
