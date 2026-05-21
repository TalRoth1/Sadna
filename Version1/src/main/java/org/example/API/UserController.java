package org.example.API;

import java.util.UUID;

import org.example.ApplicationLayer.UserService;
import org.example.ApplicationLayer.dto.ApiResponse;
import org.example.ApplicationLayer.dto.UserDTOs.LoginRequest;
import org.example.ApplicationLayer.dto.UserDTOs.RegisterRequest;
import org.example.ApplicationLayer.dto.UserDTOs.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * UserController
 *
 * Every endpoint returns ResponseEntity<ApiResponse<UserResponse>> with a
 * consistent shape: { "success": boolean, "message": string, "data": UserResponse | null }
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * POST /api/users/register
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@RequestBody RegisterRequest request) {
        try {
            UserResponse user = userService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Registered successfully", user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Register failed: system exception"));
        }
    }

    /**
     * POST /api/users/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@RequestBody LoginRequest request) {
        try {
            UserResponse user = userService.login(request);
            return ResponseEntity.ok(ApiResponse.success("Login successful", user));
        } catch (IllegalArgumentException e) {
            // Invalid credentials → 401 Unauthorized (more specific than 400)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Login failed: system exception"));
        }
    }

    /**
     * POST /api/users/{memberId}/logout
     */
    @PostMapping("/{memberId}/logout")
    public ResponseEntity<ApiResponse<UserResponse>> logout(@PathVariable UUID memberId) {
        try {
            UserResponse user = userService.logout(memberId);
            return ResponseEntity.ok(ApiResponse.success("Logout successful", user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Logout failed: system exception"));
        }
    }
}