package org.example.ApplicationLayer.dto.UserDTOs;

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
    public final int age;
    /**
     * Orthogonal capability flag.
     *
     * The User aggregate's {@code role} can only be {@code MEMBER} or
     * {@code GUEST}; system-admin status lives in a separate map on
     * {@code UserRepository}. We surface that map through this flag so
     * the frontend can render "System Admin" in the profile UI without
     * doing a second round-trip, and so any future role-gating code
     * has a single field to consult.
     */
    public final boolean isAdmin;

    public UserResponse(UUID userId, String username, String email,
                        String status, String role, int age, boolean isAdmin) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.status = status;
        this.role = role;
        this.age = age;
        this.isAdmin = isAdmin;
    }
}