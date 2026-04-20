package org.example.DomainLayer.UserAggregate;

// --- Aggregate Root: User ---
public class User {
    private String id;
    private String email; // המזהה הייחודי (מיוז קייס 2 ו-3)
    private String passwordHash; // לעולם לא שומרים סיסמה גלויה!
    private UserRole role;
    private UserStatus status;

    // בנאי ליצירת משתמש חדש (תרחיש 2)
    public User(String id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = UserRole.MEMBER;
        this.status = UserStatus.NOT_LOGGED_IN;
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
    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public UserStatus getStatus() { return status; }
}
