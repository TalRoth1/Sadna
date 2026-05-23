package org.example.API;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.example.ApplicationLayer.NotificationService;
import org.example.ApplicationLayer.dto.ApiResponse;
import org.example.ApplicationLayer.dto.NotificationDTOs.NotificationDTO;
import org.example.InfrastructureLayer.Broadcaster;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final long SSE_TIMEOUT = 30L * 60L * 1000L; // 30 minutes

    private final NotificationService notificationService;
    private final Broadcaster broadcaster;

    public NotificationController(NotificationService notificationService,
                                  Broadcaster broadcaster) {
        this.notificationService = notificationService;
        this.broadcaster = broadcaster;
    }

    // ================================================================
    //  1. Real-time notifications stream
    // ================================================================

    /**
     * Opens a real-time notifications stream for a user.
     *
     * Client example:
     *   const source = new EventSource("/api/notifications/stream/{userId}");
     *
     * This is not polling. The connection stays open and the server pushes
     * notifications when they are created.
     */
    @GetMapping(
            value = "/stream/{userId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter streamNotifications(@PathVariable("userId") String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        AtomicReference<Consumer<String>> listenerRef = new AtomicReference<>();

        Consumer<String> listener = message -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(message));
            } catch (IOException | IllegalStateException e) {
                Consumer<String> registeredListener = listenerRef.get();
                if (registeredListener != null) {
                    broadcaster.unregister(userId, registeredListener);
                }
                emitter.completeWithError(e);
            }
        };

        listenerRef.set(listener);
        broadcaster.register(userId, listener);

        emitter.onCompletion(() -> broadcaster.unregister(userId, listener));

        emitter.onTimeout(() -> {
            broadcaster.unregister(userId, listener);
            emitter.complete();
        });

        emitter.onError(error -> broadcaster.unregister(userId, listener));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Connected to notifications stream"));

            /*
             * Delayed notifications:
             * When the user connects, all unread notifications are pushed immediately.
             * They are not marked as read here. The client should call markAsRead /
             * markAllAsRead after the user actually sees them.
             */
            List<NotificationDTO> delayedNotifications =
                    notificationService.getUnreadNotifications(userId);

            for (NotificationDTO notification : delayedNotifications) {
                emitter.send(SseEmitter.event()
                        .name("delayed-notification")
                        .data(notification));
            }

        } catch (IOException e) {
            broadcaster.unregister(userId, listener);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    // ================================================================
    //  2. Notification page endpoints
    // ================================================================

    /**
     * Fetch notifications for the notifications page.
     *
     * Examples:
     *   GET /api/notifications/users/{userId}
     *   GET /api/notifications/users/{userId}?unreadOnly=true
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<List<NotificationDTO>>> getNotifications(
            @PathVariable("userId") String userId,
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly) {
        try {
            List<NotificationDTO> notifications = unreadOnly
                    ? notificationService.getUnreadNotifications(userId)
                    : notificationService.getAllNotifications(userId);

            return ResponseEntity.ok(
                    ApiResponse.success("Notifications fetched successfully", notifications)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch notifications: system exception"));
        }
    }

    // ================================================================
    //  3. Mark notifications as read
    // ================================================================

    /**
     * Marks a single notification as read.
     *
     * Example:
     *   PATCH /api/notifications/{notificationId}/read?userId={userId}
     */
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable("notificationId") UUID notificationId,
            @RequestParam("userId") String userId) {
        try {
            notificationService.markAsRead(userId, notificationId);

            return ResponseEntity.ok(
                    ApiResponse.success("Notification marked as read")
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to mark notification as read: system exception"));
        }
    }

    /**
     * Marks all user notifications as read.
     *
     * Example:
     *   PATCH /api/notifications/users/{userId}/read-all
     */
    @PatchMapping("/users/{userId}/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(
            @PathVariable("userId") String userId) {
        try {
            int count = notificationService.markAllAsRead(userId);

            return ResponseEntity.ok(
                    ApiResponse.success("All notifications marked as read", count)
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to mark notifications as read: system exception"));
        }
    }
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Void>> sendTestNotification(
            @RequestParam("userId") String userId,
            @RequestParam("message") String message) {
        try {
            notificationService.notifyUser(userId, message);
            return ResponseEntity.ok(ApiResponse.success("Test notification sent"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to send test notification"));
        }
    }
}