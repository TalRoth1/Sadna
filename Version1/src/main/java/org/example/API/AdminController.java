package org.example.API;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.ApplicationLayer.AdminService;
import org.example.ApplicationLayer.dto.ApiResponse;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminAnalyticsDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminCloseCompanyRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminCompanyDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminComplaintDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminCreateComplaintRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminQueueReleaseRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminQueueSettingsRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminQueueSnapshotDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminRemoveSubscriberRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminRespondToComplaintRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminSendSystemMessageRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminSubscriberDTO;
import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Logger logger = Logger.getLogger(AdminController.class.getName());

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminAnalyticsDTO>> getDashboard(HttpServletRequest request) {
        String action = "GET_ADMIN_DASHBOARD";

        try {
            AdminAnalyticsDTO dashboard = adminService.getAnalytics(
                    getUserId(request),
                    getUsername(request)
            );

            logger.info("action=" + action + ", status=INFO, message=Admin dashboard fetched");
            return ResponseEntity.ok(ApiResponse.success("Admin dashboard fetched", dashboard));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, message=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch admin dashboard: system exception"));
        }
    }

    @GetMapping("/companies")
    public ResponseEntity<ApiResponse<List<AdminCompanyDTO>>> getCompanies(HttpServletRequest request) {
        String action = "GET_ADMIN_COMPANIES";

        try {
            List<AdminCompanyDTO> companies = adminService.getCompanies(
                    getUserId(request),
                    getUsername(request)
            );

            logger.info("action=" + action + ", status=INFO, message=Companies fetched, resultSize=" + companies.size());
            return ResponseEntity.ok(ApiResponse.success("Companies fetched", companies));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, message=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch companies: system exception"));
        }
    }

    @DeleteMapping("/companies/{companyId}")
    public ResponseEntity<ApiResponse<Void>> closeCompany(
            @PathVariable UUID companyId,
            @RequestBody(required = false) AdminCloseCompanyRequest body,
            HttpServletRequest request) {
        String action = "CLOSE_COMPANY";

        try {
            adminService.closeCompany(
                    getUserId(request),
                    getUsername(request),
                    companyId
            );

            logger.info("action=" + action + ", status=INFO, message=Company closed, companyId=" + companyId);
            return ResponseEntity.ok(ApiResponse.success("Company closed successfully"));
        } catch (IllegalStateException e) {
            logger.severe("action=" + action + ", status=ERROR, companyId=" + companyId + ", message=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, companyId=" + companyId + ", message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, companyId=" + companyId + ", message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to close company: system exception"));
        }
    }

    @GetMapping("/subscribers")
    public ResponseEntity<ApiResponse<List<AdminSubscriberDTO>>> getSubscribers(HttpServletRequest request) {
        String action = "GET_ADMIN_SUBSCRIBERS";

        try {
            List<AdminSubscriberDTO> subscribers = adminService.getSubscribers(
                    getUserId(request),
                    getUsername(request)
            );

            logger.info("action=" + action + ", status=INFO, message=Subscribers fetched, resultSize=" + subscribers.size());
            return ResponseEntity.ok(ApiResponse.success("Subscribers fetched", subscribers));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, message=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch subscribers: system exception"));
        }
    }

    @DeleteMapping("/subscribers/{username}")
    public ResponseEntity<ApiResponse<Void>> removeSubscriber(
            @PathVariable String username,
            @RequestBody(required = false) AdminRemoveSubscriberRequest body,
            HttpServletRequest request) {
        String action = "REMOVE_SUBSCRIBER";

        try {
            adminService.removeSubscriber(
                    getUserId(request),
                    getUsername(request),
                    username
            );

            logger.info("action=" + action + ", status=INFO, message=Subscriber removed, username=" + username);
            return ResponseEntity.ok(ApiResponse.success("Subscriber removed successfully"));
        } catch (IllegalStateException e) {
            logger.severe("action=" + action + ", status=ERROR, username=" + username + ", message=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, username=" + username + ", message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, username=" + username + ", message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to remove subscriber: system exception"));
        }
    }

    @PostMapping("/messages/system")
    public ResponseEntity<ApiResponse<Void>> sendSystemMessage(
            @RequestBody AdminSendSystemMessageRequest body,
            HttpServletRequest request) {
        String action = "SEND_SYSTEM_MESSAGE";

        try {
            adminService.sendSystemMessage(
                    getUserId(request),
                    getUsername(request),
                    body.message
            );

            logger.info("action=" + action + ", status=INFO, message=System message sent");
            return ResponseEntity.ok(ApiResponse.success("System message sent successfully"));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to send system message: system exception"));
        }
    }

    @GetMapping("/purchases")
    public ResponseEntity<ApiResponse<List<PurchaseHistoryDTO>>> getAllPurchaseHistory(HttpServletRequest request) {
        String action = "GET_ALL_PURCHASE_HISTORY";

        try {
            List<PurchaseHistoryDTO> history = adminService.getAllPurchaseHistory(
                    getUserId(request),
                    getUsername(request)
            );

            logger.info("action=" + action + ", status=INFO, message=Purchase history fetched, resultSize=" + history.size());
            return ResponseEntity.ok(ApiResponse.success("Purchase history fetched", history));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, message=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch purchase history: system exception"));
        }
    }

    @GetMapping("/purchases/filter")
    public ResponseEntity<ApiResponse<List<PurchaseHistoryDTO>>> getPurchaseHistoryByFilter(
            @RequestParam String type,
            @RequestParam(required = false) UUID id,
            HttpServletRequest request) {
        String action = "GET_PURCHASE_HISTORY_BY_FILTER";

        try {
            List<PurchaseHistoryDTO> history = adminService.getPurchaseHistoryByFilter(
                    getUserId(request),
                    getUsername(request),
                    type,
                    id
            );

            logger.info("action=" + action + ", status=INFO, message=Filtered purchase history fetched, type=" + type + ", id=" + id + ", resultSize=" + history.size());
            return ResponseEntity.ok(ApiResponse.success("Filtered purchase history fetched", history));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, type=" + type + ", id=" + id + ", message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, type=" + type + ", id=" + id + ", message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch filtered purchase history: system exception"));
        }
    }

    @GetMapping("/purchases/users/{userId}")
    public ResponseEntity<ApiResponse<List<PurchaseHistoryDTO>>> getPurchaseHistoryByUser(
            @PathVariable UUID userId,
            HttpServletRequest request) {
        String action = "GET_PURCHASE_HISTORY_BY_USER";

        try {
            List<PurchaseHistoryDTO> history = adminService.getPurchaseHistoryByUser(
                    getUserId(request),
                    getUsername(request),
                    userId
            );

            logger.info("action=" + action + ", status=INFO, message=User purchase history fetched, userId=" + userId + ", resultSize=" + history.size());
            return ResponseEntity.ok(ApiResponse.success("User purchase history fetched", history));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, userId=" + userId + ", message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, userId=" + userId + ", message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch user purchase history: system exception"));
        }
    }

    @GetMapping("/purchases/events/{eventId}")
    public ResponseEntity<ApiResponse<List<PurchaseHistoryDTO>>> getPurchaseHistoryByEvent(
            @PathVariable UUID eventId,
            HttpServletRequest request) {
        String action = "GET_PURCHASE_HISTORY_BY_EVENT";

        try {
            List<PurchaseHistoryDTO> history = adminService.getPurchaseHistoryByEvent(
                    getUserId(request),
                    getUsername(request),
                    eventId
            );

            logger.info("action=" + action + ", status=INFO, message=Event purchase history fetched, eventId=" + eventId + ", resultSize=" + history.size());
            return ResponseEntity.ok(ApiResponse.success("Event purchase history fetched", history));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, eventId=" + eventId + ", message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, eventId=" + eventId + ", message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch event purchase history: system exception"));
        }
    }

    @GetMapping("/purchases/companies/{companyId}")
    public ResponseEntity<ApiResponse<List<PurchaseHistoryDTO>>> getPurchaseHistoryByCompany(
            @PathVariable UUID companyId,
            HttpServletRequest request) {
        String action = "GET_PURCHASE_HISTORY_BY_COMPANY";

        try {
            List<PurchaseHistoryDTO> history = adminService.getPurchaseHistoryByCompany(
                    getUserId(request),
                    getUsername(request),
                    companyId
            );

            logger.info("action=" + action + ", status=INFO, message=Company purchase history fetched, companyId=" + companyId + ", resultSize=" + history.size());
            return ResponseEntity.ok(ApiResponse.success("Company purchase history fetched", history));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, companyId=" + companyId + ", message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, companyId=" + companyId + ", message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch company purchase history: system exception"));
        }
    }

    @PostMapping("/complaints")
    public ResponseEntity<ApiResponse<AdminComplaintDTO>> createComplaint(
            @RequestBody AdminCreateComplaintRequest body,
            HttpServletRequest request) {
        String action = "CREATE_COMPLAINT";

        try {
            AdminComplaintDTO complaint = adminService.createComplaint(
                    getUserId(request),
                    getUsername(request),
                    body.title,
                    body.description
            );

            logger.info("action=" + action + ", status=INFO, message=Complaint created, complaintId=" + complaint.id);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Complaint created successfully", complaint));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create complaint: system exception"));
        }
    }

    @GetMapping("/complaints")
    public ResponseEntity<ApiResponse<List<AdminComplaintDTO>>> getComplaints(HttpServletRequest request) {
        String action = "GET_COMPLAINTS";

        try {
            List<AdminComplaintDTO> complaints = adminService.getAllComplaints(
                    getUserId(request),
                    getUsername(request)
            );

            logger.info("action=" + action + ", status=INFO, message=Complaints fetched, resultSize=" + complaints.size());
            return ResponseEntity.ok(ApiResponse.success("Complaints fetched", complaints));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, message=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch complaints: system exception"));
        }
    }

    @GetMapping("/complaints/open")
    public ResponseEntity<ApiResponse<List<AdminComplaintDTO>>> getOpenComplaints(HttpServletRequest request) {
        String action = "GET_OPEN_COMPLAINTS";

        try {
            List<AdminComplaintDTO> complaints = adminService.getOpenComplaints(
                    getUserId(request),
                    getUsername(request)
            );

            logger.info("action=" + action + ", status=INFO, message=Open complaints fetched, resultSize=" + complaints.size());
            return ResponseEntity.ok(ApiResponse.success("Open complaints fetched", complaints));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, message=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch open complaints: system exception"));
        }
    }

    @PatchMapping("/complaints/{complaintId}/response")
    public ResponseEntity<ApiResponse<AdminComplaintDTO>> respondToComplaint(
            @PathVariable UUID complaintId,
            @RequestBody AdminRespondToComplaintRequest body,
            HttpServletRequest request) {
        String action = "RESPOND_TO_COMPLAINT";

        try {
            AdminComplaintDTO complaint = adminService.respondToComplaint(
                    getUserId(request),
                    getUsername(request),
                    complaintId,
                    body.response
            );

            logger.info("action=" + action + ", status=INFO, message=Complaint response saved, complaintId=" + complaintId);
            return ResponseEntity.ok(ApiResponse.success("Complaint response saved", complaint));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, complaintId=" + complaintId + ", message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, complaintId=" + complaintId + ", message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to respond to complaint: system exception"));
        }
    }

    @PatchMapping("/complaints/{complaintId}/close")
    public ResponseEntity<ApiResponse<AdminComplaintDTO>> closeComplaint(
            @PathVariable UUID complaintId,
            HttpServletRequest request) {
        String action = "CLOSE_COMPLAINT";

        try {
            AdminComplaintDTO complaint = adminService.closeComplaint(
                    getUserId(request),
                    getUsername(request),
                    complaintId
            );

            logger.info("action=" + action + ", status=INFO, message=Complaint closed, complaintId=" + complaintId);
            return ResponseEntity.ok(ApiResponse.success("Complaint closed", complaint));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, complaintId=" + complaintId + ", message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, complaintId=" + complaintId + ", message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to close complaint: system exception"));
        }
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<AdminAnalyticsDTO>> getAnalytics(HttpServletRequest request) {
        String action = "GET_ANALYTICS";

        try {
            AdminAnalyticsDTO analytics = adminService.getAnalytics(
                    getUserId(request),
                    getUsername(request)
            );

            logger.info("action=" + action + ", status=INFO, message=Analytics fetched");
            return ResponseEntity.ok(ApiResponse.success("Analytics fetched", analytics));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, message=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch analytics: system exception"));
        }
    }

    @GetMapping("/queues")
    public ResponseEntity<ApiResponse<List<AdminQueueSnapshotDTO>>> getQueues(HttpServletRequest request) {
        String action = "GET_QUEUES";

        try {
            List<AdminQueueSnapshotDTO> queues = adminService.getAllQueues(
                    getUserId(request),
                    getUsername(request)
            );

            logger.info("action=" + action + ", status=INFO, message=Queues fetched, resultSize=" + queues.size());
            return ResponseEntity.ok(ApiResponse.success("Queues fetched", queues));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, message=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch queues: system exception"));
        }
    }

    @GetMapping("/queues/{eventId}")
    public ResponseEntity<ApiResponse<AdminQueueSnapshotDTO>> getQueue(
            @PathVariable UUID eventId,
            HttpServletRequest request) {
        String action = "GET_QUEUE";

        try {
            AdminQueueSnapshotDTO queue = adminService.getQueue(
                    getUserId(request),
                    getUsername(request),
                    eventId
            );

            logger.info("action=" + action + ", status=INFO, message=Queue fetched, eventId=" + eventId);
            return ResponseEntity.ok(ApiResponse.success("Queue fetched", queue));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, eventId=" + eventId + ", message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, eventId=" + eventId + ", message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch queue: system exception"));
        }
    }

    @PostMapping("/queues/{eventId}/release")
    public ResponseEntity<ApiResponse<AdminQueueSnapshotDTO>> releaseQueueBatch(
            @PathVariable UUID eventId,
            @RequestBody AdminQueueReleaseRequest body,
            HttpServletRequest request) {
        String action = "RELEASE_QUEUE_BATCH";

        try {
            AdminQueueSnapshotDTO queue = adminService.releaseQueueBatch(
                    getUserId(request),
                    getUsername(request),
                    eventId,
                    body.batchSize
            );

            logger.info("action=" + action + ", status=INFO, message=Queue batch released, eventId=" + eventId + ", batchSize=" + body.batchSize);
            return ResponseEntity.ok(ApiResponse.success("Queue batch released", queue));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, eventId=" + eventId + ", message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, eventId=" + eventId + ", message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to release queue batch: system exception"));
        }
    }

    @DeleteMapping("/queues/{eventId}")
    public ResponseEntity<ApiResponse<AdminQueueSnapshotDTO>> clearQueue(
            @PathVariable UUID eventId,
            HttpServletRequest request) {
        String action = "CLEAR_QUEUE";

        try {
            AdminQueueSnapshotDTO queue = adminService.clearQueue(
                    getUserId(request),
                    getUsername(request),
                    eventId
            );

            logger.info("action=" + action + ", status=INFO, message=Queue cleared, eventId=" + eventId);
            return ResponseEntity.ok(ApiResponse.success("Queue cleared", queue));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, eventId=" + eventId + ", message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, eventId=" + eventId + ", message=System exception, error=" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to clear queue: system exception"));
        }
    }

    @PatchMapping("/queues/settings")
    public ResponseEntity<ApiResponse<AdminAnalyticsDTO>> updateQueueSettings(
            @RequestBody AdminQueueSettingsRequest body,
            HttpServletRequest request) {
        String action = "UPDATE_QUEUE_SETTINGS";

        try {
            AdminAnalyticsDTO analytics = adminService.updateQueueSettings(
                    getUserId(request),
                    getUsername(request),
                    body
            );

            logger.info("action=" + action + ", status=INFO, message=Queue settings updated");
            return ResponseEntity.ok(ApiResponse.success("Queue settings updated", analytics));
        } catch (IllegalArgumentException e) {
            logger.severe("action=" + action + ", status=ERROR, message=" + e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.severe("action=" + action + ", status=ERROR, message=System exception, error=" + e.getMessage());
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