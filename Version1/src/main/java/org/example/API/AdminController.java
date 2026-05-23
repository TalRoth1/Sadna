package org.example.API;

import java.util.List;
import java.util.UUID;

import org.example.ApplicationLayer.AdminService;
import org.example.ApplicationLayer.dto.ApiResponse;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminAnalyticsDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminCloseCompanyRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminComplaintDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminCreateComplaintRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminQueueReleaseRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminQueueSettingsRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminQueueSnapshotDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminRemoveSubscriberRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminRespondToComplaintRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminSendSystemMessageRequest;
import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminAnalyticsDTO>> getDashboard(HttpServletRequest request) {
        try {
            AdminAnalyticsDTO dashboard = adminService.getAnalytics(
                    getUserId(request),
                    getUsername(request)
            );

            return ResponseEntity.ok(ApiResponse.success("Admin dashboard fetched", dashboard));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch admin dashboard: system exception"));
        }
    }

    @DeleteMapping("/companies/{companyId}")
    public ResponseEntity<ApiResponse<Void>> closeCompany(
            @PathVariable UUID companyId,
            @RequestBody(required = false) AdminCloseCompanyRequest body,
            HttpServletRequest request) {
        try {
            adminService.closeCompany(getUserId(request), getUsername(request), companyId);
            return ResponseEntity.ok(ApiResponse.success("Company closed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to close company"));
        }
    }

    @DeleteMapping("/subscribers/{username}")
    public ResponseEntity<ApiResponse<Void>> removeSubscriber(
            @PathVariable String username,
            @RequestBody(required = false) AdminRemoveSubscriberRequest body,
            HttpServletRequest request) {
        try {
            adminService.removeSubscriber(getUserId(request), getUsername(request), username);
            return ResponseEntity.ok(ApiResponse.success("Subscriber removed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Not authorized to remove subscriber"));
        }
    }

    @PostMapping("/messages/system")
    public ResponseEntity<ApiResponse<Void>> sendSystemMessage(
            @RequestBody AdminSendSystemMessageRequest body,
            HttpServletRequest request) {
        try {
            adminService.sendSystemMessage(getUserId(request), getUsername(request), body.message);
            return ResponseEntity.ok(ApiResponse.success("System message sent successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to send system message: system exception"));
        }
    }

    @GetMapping("/purchases")
    public ResponseEntity<ApiResponse<List<PurchaseHistoryDTO>>> getAllPurchaseHistory(HttpServletRequest request) {
        try {
            List<PurchaseHistoryDTO> history = adminService.getAllPurchaseHistory(
                    getUserId(request),
                    getUsername(request)
            );

            return ResponseEntity.ok(ApiResponse.success("Purchase history fetched", history));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch purchase history: system exception"));
        }
    }

    @GetMapping("/purchases/users/{userId}")
    public ResponseEntity<ApiResponse<List<PurchaseHistoryDTO>>> getPurchaseHistoryByUser(
            @PathVariable UUID userId,
            HttpServletRequest request) {
        try {
            List<PurchaseHistoryDTO> history = adminService.getPurchaseHistoryByUser(
                    getUserId(request),
                    getUsername(request),
                    userId
            );

            return ResponseEntity.ok(ApiResponse.success("User purchase history fetched", history));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch user purchase history: system exception"));
        }
    }

    @GetMapping("/purchases/events/{eventId}")
    public ResponseEntity<ApiResponse<List<PurchaseHistoryDTO>>> getPurchaseHistoryByEvent(
            @PathVariable UUID eventId,
            HttpServletRequest request) {
        try {
            List<PurchaseHistoryDTO> history = adminService.getPurchaseHistoryByEvent(
                    getUserId(request),
                    getUsername(request),
                    eventId
            );

            return ResponseEntity.ok(ApiResponse.success("Event purchase history fetched", history));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch event purchase history: system exception"));
        }
    }

    @GetMapping("/purchases/companies/{companyId}")
    public ResponseEntity<ApiResponse<List<PurchaseHistoryDTO>>> getPurchaseHistoryByCompany(
            @PathVariable UUID companyId,
            HttpServletRequest request) {
        try {
            List<PurchaseHistoryDTO> history = adminService.getPurchaseHistoryByCompany(
                    getUserId(request),
                    getUsername(request),
                    companyId
            );

            return ResponseEntity.ok(ApiResponse.success("Company purchase history fetched", history));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch company purchase history: system exception"));
        }
    }

    @PostMapping("/complaints")
    public ResponseEntity<ApiResponse<AdminComplaintDTO>> createComplaint(
            @RequestBody AdminCreateComplaintRequest body,
            HttpServletRequest request) {
        try {
            AdminComplaintDTO complaint = adminService.createComplaint(
                    getUserId(request),
                    getUsername(request),
                    body.title,
                    body.description
            );

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Complaint created successfully", complaint));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create complaint: system exception"));
        }
    }

    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse<List<AdminComplaintDTO>>> getComplaints(HttpServletRequest request) {
        try {
            List<AdminComplaintDTO> complaints = adminService.getAllComplaints(
                    getUserId(request),
                    getUsername(request)
            );

            return ResponseEntity.ok(ApiResponse.success("Complaints fetched", complaints));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch complaints: system exception"));
        }
    }

    @PatchMapping("/complaints/{complaintId}/response")
    public ResponseEntity<ApiResponse<AdminComplaintDTO>> respondToComplaint(
            @PathVariable UUID complaintId,
            @RequestBody AdminRespondToComplaintRequest body,
            HttpServletRequest request) {
        try {
            AdminComplaintDTO complaint = adminService.respondToComplaint(
                    getUserId(request),
                    getUsername(request),
                    complaintId,
                    body.response
            );

            return ResponseEntity.ok(ApiResponse.success("Complaint response saved", complaint));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to respond to complaint: system exception"));
        }
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<AdminAnalyticsDTO>> getAnalytics(HttpServletRequest request) {
        try {
            AdminAnalyticsDTO analytics = adminService.getAnalytics(
                    getUserId(request),
                    getUsername(request)
            );

            return ResponseEntity.ok(ApiResponse.success("Analytics fetched", analytics));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch analytics: system exception"));
        }
    }

    @GetMapping("/queues")
    public ResponseEntity<ApiResponse<List<AdminQueueSnapshotDTO>>> getQueues(HttpServletRequest request) {
        try {
            List<AdminQueueSnapshotDTO> queues = adminService.getAllQueues(
                    getUserId(request),
                    getUsername(request)
            );

            return ResponseEntity.ok(ApiResponse.success("Queues fetched", queues));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch queues: system exception"));
        }
    }

    @GetMapping("/queues/{eventId}")
    public ResponseEntity<ApiResponse<AdminQueueSnapshotDTO>> getQueue(
            @PathVariable UUID eventId,
            HttpServletRequest request) {
        try {
            AdminQueueSnapshotDTO queue = adminService.getQueue(
                    getUserId(request),
                    getUsername(request),
                    eventId
            );

            return ResponseEntity.ok(ApiResponse.success("Queue fetched", queue));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch queue: system exception"));
        }
    }

    @PostMapping("/queues/{eventId}/release")
    public ResponseEntity<ApiResponse<AdminQueueSnapshotDTO>> releaseQueueBatch(
            @PathVariable UUID eventId,
            @RequestBody AdminQueueReleaseRequest body,
            HttpServletRequest request) {
        try {
            AdminQueueSnapshotDTO queue = adminService.releaseQueueBatch(
                    getUserId(request),
                    getUsername(request),
                    eventId,
                    body.batchSize
            );

            return ResponseEntity.ok(ApiResponse.success("Queue batch released", queue));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to release queue batch: system exception"));
        }
    }

    @DeleteMapping("/queues/{eventId}")
    public ResponseEntity<ApiResponse<AdminQueueSnapshotDTO>> clearQueue(
            @PathVariable UUID eventId,
            HttpServletRequest request) {
        try {
            AdminQueueSnapshotDTO queue = adminService.clearQueue(
                    getUserId(request),
                    getUsername(request),
                    eventId
            );

            return ResponseEntity.ok(ApiResponse.success("Queue cleared", queue));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to clear queue: system exception"));
        }
    }

    @PatchMapping("/queues/settings")
    public ResponseEntity<ApiResponse<AdminAnalyticsDTO>> updateQueueSettings(
            @RequestBody AdminQueueSettingsRequest body,
            HttpServletRequest request) {
        try {
            AdminAnalyticsDTO analytics = adminService.updateQueueSettings(
                    getUserId(request),
                    getUsername(request),
                    body
            );

            return ResponseEntity.ok(ApiResponse.success("Queue settings updated", analytics));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update queue settings: system exception"));
        }
    }

    private UUID getUserId(HttpServletRequest request) {
        Object value = request.getAttribute("userId");
        if (!(value instanceof UUID userId)) {
            throw new IllegalArgumentException("Missing authenticated user ID");
        }

        return userId;
    }

    private String getUsername(HttpServletRequest request) {
        Object value = request.getAttribute("username");
        if (!(value instanceof String username) || username.isBlank()) {
            throw new IllegalArgumentException("Missing authenticated username");
        }

        return username;
    }
}