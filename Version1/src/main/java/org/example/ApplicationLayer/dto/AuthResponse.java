package org.example.ApplicationLayer.dto;

import java.util.UUID;

import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;

/**
 * AuthResponse
 *
 * Payload returned by authentication endpoints (guest entry, register, login).
 * Carries the signed JWT session token plus the user that the token represents.
 *
 * The legacy fields (isSuccess, message, userId) are preserved for backward
 * compatibility with any existing callers.
 */
public class AuthResponse {
    public boolean isSuccess;
    public String message;
    public UUID userId;

    /** Signed JWT session token (compact form). To be sent back by the client as Authorization: Bearer <token>. */
    public String token;

    /** Full user payload, so the client doesn't need a follow-up call to learn its identity/role. */
    public UserResponse user;

    public AuthResponse() {
    }

    public AuthResponse(boolean b, String message, UUID memberId) {
        this.isSuccess = b;
        this.message = message;
        this.userId = memberId;
    }

    public AuthResponse(boolean isSuccess, String message, String token, UserResponse user) {
        this.isSuccess = isSuccess;
        this.message = message;
        this.token = token;
        this.user = user;
        this.userId = user == null ? null : user.userId;
    }
}
