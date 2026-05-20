package org.example.ApplicationLayer.UserDTOs;

import java.util.UUID;

/**
 * Payload returned by login / register / logout endpoints.
 *
 * No success/message fields — those are carried by the outer
 * ApiResponse<UserResponse> wrapper. This DTO is pure data.
 *
 * IMPORTANT: never include the password hash. This DTO goes to the client.
 *
 * Note: status and role are stored as Strings (the enum name) rather
 * than as enums directly, so the DTO doesn't depend on Domain types.
 */
public class UserResponse {
    public final UUID userId;
    public final String username;
    public final String email;
    public final String status;   // "LOGGED_IN" / "NOT_LOGGED_IN"
    public final String role;     // "MEMBER" / "GUEST" / etc.
    public final float age;

    public UserResponse(UUID userId, String username, String email,
                        String status, String role, float age) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.status = status;
        this.role = role;
        this.age = age;
    }
}