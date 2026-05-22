package org.example.API;

import jakarta.servlet.http.HttpServletRequest;
import org.example.ApplicationLayer.NotificationService;
import org.example.ApplicationLayer.dto.ApiResponse;
import org.example.ApplicationLayer.dto.NotificationDTOs.NotificationResponse;
import org.example.ApplicationLayer.dto.NotificationDTOs.SendNotificationRequest;
import org.example.ApplicationLayer.dto.NotificationDTOs.UnreadCountResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * NotificationController
 *
 * REST is used for:
 *  - fetching stored notifications
 *  - fetching unread notifications
 *  - marking notifications as read
 *  - manual/admin notification sending
 *
 * Real-time delivery should be handled by INotifier implementation,
 * for example WebSocketNotifier.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // ================================================================
    //  1. Current user's notifications
    // ================================================================

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyNotifications(
            HttpServletRequest httpRequest) {
        try {
            String userId = extractUserId(httpRequest);

            List<NotificationResponse> notifications =
                    notificationService.getNotificationsForUser(userId);

            return ResponseEntity.ok(
                    ApiResponse.success("Notifications fetched successfully", notifications)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch notifications: system exception"));
        }
    }

    @GetMapping("/me/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getMyUnreadNotifications(
            HttpServletRequest httpRequest) {
        try {
            String userId = extractUserId(httpRequest);

            List<NotificationResponse> notifications =
                    notificationService.getUnreadNotificationsForUser(userId);

            return ResponseEntity.ok(
                    ApiResponse.success("Unread notifications fetched successfully", notifications)
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch unread notifications: system exception"));
        }
    }

    @GetMapping("/me/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getMyUnreadCount(
            HttpServletRequest httpRequest) {
        try {
            String userId = extractUserId(httpRequest);

            long count = notificationService.getUnreadCount(userId);

            return ResponseEntity.ok(
                    ApiResponse.success("Unread notifications count fetched successfully",
                            new UnreadCountResponse(count))
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch unread notifications count: system exception"));
        }
    }

    // ================================================================
    //  2. Mark as read
    // ================================================================

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable("notificationId") UUID notificationId,
            HttpServletRequest httpRequest) {
        try {
            String userId = extractUserId(httpRequest);

            notificationService.markAsRead(userId, notificationId);

            return ResponseEntity.ok(
                    ApiResponse.success("Notification marked as read successfully")
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to mark notification as read: system exception"));
        }
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(
            HttpServletRequest httpRequest) {
        try {
            String userId = extractUserId(httpRequest);

            notificationService.markAllAsRead(userId);

            return ResponseEntity.ok(
                    ApiResponse.success("All notifications marked as read successfully")
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to mark all notifications as read: system exception"));
        }
    }

    // ================================================================
    //  3. Manual notification sending
    // ================================================================

    @PostMapping("/admin/send")
    public ResponseEntity<ApiResponse<NotificationResponse>> sendManualNotification(
            @RequestBody SendNotificationRequest request) {
        try {
            NotificationResponse notification =
                    notificationService.sendManualNotification(request);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Notification created successfully", notification));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to send notification: system exception"));
        }
    }

    private String extractUserId(HttpServletRequest httpRequest) {
        Object userIdAttribute = httpRequest.getAttribute("userId");

        if (userIdAttribute == null) {
            throw new IllegalArgumentException("Missing or invalid token");
        }

        if (userIdAttribute instanceof UUID uuid) {
            return uuid.toString();
        }

        return userIdAttribute.toString();
    }
}