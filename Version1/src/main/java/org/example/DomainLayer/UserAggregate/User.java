package org.example.DomainLayer.UserAggregate;

import java.util.UUID;

// --- Aggregate Root: User ---
public class User {
    private UUID id;
    private String username;
    private String email; // המזהה הייחודי (מיוז קייס 2 ו-3)
    private String passwordHash; // לעולם לא שומרים סיסמה גלויה!
    private UserRole role;
    private UserStatus status;
    private float age;

    // בנאי ליצירת משתמש חדש (תרחיש 2)
    public User(UUID id,String username, String email, String passwordHash, float age) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = UserRole.MEMBER;
        this.status = UserStatus.NOT_LOGGED_IN;
        this.age = age;
    }

    // --- Domain Logic (Rich Model) ---

    // מיוז קייס 3: כניסה למערכת
    public void login() {
        if (this.status == UserStatus.NOT_LOGGED_IN) {
            throw new IllegalStateException("הבקשה נדחתה: המשתמש כבר נמצא בסטטוס מנותק.");
        }
        this.status = UserStatus.LOGGED_IN;
        this.role = UserRole.MEMBER;
    }

    // מיוז קייס 1: יציאה ועזיבת המערכת
    public void logout() {
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
