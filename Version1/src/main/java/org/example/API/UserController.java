package org.example.API;

import org.example.ApplicationLayer.UserService;
import org.example.ApplicationLayer.dto.AuthResponse;
import org.example.ApplicationLayer.dto.LoginRequest;
import org.example.ApplicationLayer.dto.RegisterRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    // הזרקת תלויות (Dependency Injection) לשכבת ה-Application
    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Endpoint: POST /api/users/register
     * מטפל בהרשמת משתמש חדש
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        // קריאה לשירות וקבלת התשובה העסקית
        AuthResponse response = userService.register(request);

        // עטיפת התשובה בסטטוס HTTP מתאים
        if (response.success) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response); // 201 Created
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response); // 400 Bad Request
        }
    }

    /**
     * Endpoint: POST /api/users/login
     * מטפל בהתחברות משתמש קיים
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = userService.login(request);

        if (response.success) {
            return ResponseEntity.ok(response); // 200 OK
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response); // 401 Unauthorized
        }
    }

    /**
     * Endpoint: POST /api/users/{memberId}/logout
     * מטפל בהתנתקות משתמש מהמערכת
     */
    @PostMapping("/{memberId}/logout")
    public ResponseEntity<AuthResponse> logout(@PathVariable UUID memberId) {
        AuthResponse response = userService.logout(memberId);

        if (response.success) {
            return ResponseEntity.ok(response); // 200 OK
        } else {
            // במידה והשגיאה היא בגלל שהמשתמש לא קיים או בעיית הרשאות
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response); // 400 Bad Request
        }
    }
}