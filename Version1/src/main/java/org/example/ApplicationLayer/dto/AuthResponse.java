package org.example.ApplicationLayer.dto;

import java.util.UUID;

public class AuthResponse {
    public boolean isSuccess;
    public String message;
    public UUID userId;

    /** Signed JWT to send back to the client. {@code null} on failure / when not applicable. */
    public String token;

    /** Auth scheme the client should use, e.g. {@code "Bearer"}. */
    public String tokenType;

    /** Token lifetime in seconds (matches OAuth2-style {@code expires_in}). */
    public long expiresInSeconds;

    public AuthResponse() {
    }

    public AuthResponse(boolean b, String message, UUID memberId) {
        this.isSuccess = b;
        this.message = message;
        this.userId = memberId;
    }

    public AuthResponse(boolean b,
                        String message,
                        UUID memberId,
                        String token,
                        String tokenType,
                        long expiresInSeconds) {
        this.isSuccess = b;
        this.message = message;
        this.userId = memberId;
        this.token = token;
        this.tokenType = tokenType;
        this.expiresInSeconds = expiresInSeconds;
    }
}
