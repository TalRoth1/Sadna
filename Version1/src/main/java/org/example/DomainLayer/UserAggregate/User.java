package org.example.DomainLayer.UserAggregate;

import java.util.UUID;

// --- Aggregate Root: User ---
public class User {
    private UUID id;
    private String username;
    private String email; // identifier
    private String passwordHash;
    private UserRole role;
    private UserStatus status;
    private float age;


    public User(UUID id,String username, String email, String passwordHash, float age) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = UserRole.MEMBER;
        this.status = UserStatus.NOT_LOGGED_IN;
        this.age = age;
    }


    public void login() {
        // שומר סף: זורק שגיאה רק אם המשתמש מנסה להתחבר כשהוא כבר מחובר
        if (this.status == UserStatus.LOGGED_IN) {
            throw new IllegalStateException("The user is already logged in.");
        }
        this.status = UserStatus.LOGGED_IN;
        this.role = UserRole.MEMBER;
    }


    public void logout() {
        if (this.status == UserStatus.NOT_LOGGED_IN) {
            throw new IllegalStateException("The user is already logged out.");
        }
        this.status = UserStatus.NOT_LOGGED_IN;
        this.role = UserRole.GUEST;
    }





    // Getters...
    public UUID getId() { return id; }
    public String getUsername() {return username;}
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public UserStatus getStatus() { return status; }
    public float getAge() { return age; }
    public UserRole getRole() {return role;}
}
