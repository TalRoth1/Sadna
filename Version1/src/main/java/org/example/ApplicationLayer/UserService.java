package org.example.ApplicationLayer;

import org.example.ApplicationLayer.dto.AuthResponse;
import org.example.ApplicationLayer.dto.LoginRequest;
import org.example.ApplicationLayer.dto.RegisterRequest;
import org.example.DomainLayer.IAuthenticationGateway;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.UserAggregate.User;

import java.util.UUID;

public class UserService{

    private final IUserRepository userRepository;
    private final IAuthenticationGateway authGateway;

    public UserService(IUserRepository userRepository, IAuthenticationGateway authGateway) {
        this.userRepository = userRepository;
        this.authGateway = authGateway;
    }

    public AuthResponse logout(UUID memberId) {
        try {
            User user = userRepository.getUser(memberId).get();
            if (user == null) {
                return new AuthResponse(false, "הבקשה נדחתה: מזהה המשתמש אינו קיים.", null);
            }

            user.logout();
            userRepository.add(user);

            return new AuthResponse(true, "התנתקת בהצלחה.", user.getId());
        } catch (IllegalStateException e) {
            return new AuthResponse(false, e.getMessage(), memberId);
        } catch (Exception e) {
            return new AuthResponse(false, "שגיאת מערכת בעת התנתקות.", memberId);
        }
    }

    public AuthResponse register(RegisterRequest request) {
        try {
            if (request.email == null || request.email.isEmpty() || request.plainPassword == null) {
                return new AuthResponse(false, "פרטים חסרים.", null);
            }

            if (userRepository.existsByEmail(request.email)) {
                return new AuthResponse(false, "האימייל כבר קיים במערכת.", null);
            }

            String hashedPassword = authGateway.hashPassword(request.plainPassword);
            UUID newUserId = UUID.randomUUID();
            User newUser = new User(newUserId,request.username, request.email, hashedPassword, request.age);

            userRepository.add(newUser);
            return new AuthResponse(true, "נרשמת בהצלחה!", newUser.getId());
        } catch (Exception e) {
            return new AuthResponse(false, "שגיאת מערכת בהרשמה.", null);
        }
    }

    public AuthResponse login(LoginRequest request) {
        try {
            // 1. בדיקת תקינות קלט בסיסית
            if (request.email == null || request.plainPassword == null) {
                return new AuthResponse(false, "חובה להזין אימייל וסיסמה.", null);
            }

            // 2. חיפוש המנוי במאגר (אלטרנטיבה 7.2א: זיהוי נכשל)
            User user = userRepository.findByEmail(request.email).get();
            if (user == null) {
                // מטעמי אבטחה מחזירים הודעה כללית "פרטים שגויים"
                return new AuthResponse(false, "אימייל או סיסמה שגויים.", null);
            }

            // 3. אימות הסיסמה (אלטרנטיבה 7.3א: אימות נכשל)
            boolean isPasswordCorrect = authGateway.verifyPassword(request.plainPassword, user.getPasswordHash());
            if (!isPasswordCorrect) {
                // כאן נהוג לרשום לוג של ניסיון כושל (לטובת הגנה מפריצות)
                System.out.println("LOG: Failed login attempt for user: " + request.email);
                return new AuthResponse(false, "אימייל או סיסמה שגויים.", null);
            }

            // 4. עדכון ישות הדומיין (שינוי סטטוס ל-LoggedIn ותפקיד ל-Member)
            user.login();

            // 5. שמירה במאגר
            userRepository.add(user);

            // 6. החזרת אישור הצלחה
            return new AuthResponse(true, "התחברת בהצלחה!", user.getId());

        } catch (Exception e) {
            return new AuthResponse(false, "שגיאת שרת פנימית בעת ניסיון התחברות.", null);
        }
    }
}