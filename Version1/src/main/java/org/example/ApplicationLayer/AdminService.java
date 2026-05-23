package org.example.ApplicationLayer;

import java.util.List;
import java.util.UUID;

import org.example.ApplicationLayer.dto.AdminDTOs.AdminAnalyticsDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminComplaintDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminQueueSettingsRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminQueueSnapshotDTO;
import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;
import org.example.DomainLayer.IAdminRepository;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.IPurchaseRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.AdminAggregate.AdminActionLog;
import org.example.DomainLayer.AdminAggregate.AdminComplaint;
import org.example.DomainLayer.AdminAggregate.SystemAnalyticsSnapshot;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
    private final IUserRepository userRepository;
    private final ICompanyRepository companyRepository;
    private final IPurchaseRepository purchaseRepository;
    private final IHistoryRepository historyRepository;
    private final IAdminRepository adminRepository;

    private final CompanyService companyService;
    private final PurchaseService purchaseService;
    private final UserService userService;
    private final QueueManager queueManager;

    public AdminService(
            IUserRepository userRepository,
            ICompanyRepository companyRepository,
            IPurchaseRepository purchaseRepository,
            IHistoryRepository historyRepository,
            IAdminRepository adminRepository,
            CompanyService companyService,
            PurchaseService purchaseService,
            UserService userService,
            QueueManager queueManager) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.purchaseRepository = purchaseRepository;
        this.historyRepository = historyRepository;
        this.adminRepository = adminRepository;
        this.companyService = companyService;
        this.purchaseService = purchaseService;
        this.userService = userService;
        this.queueManager = queueManager;
    }

    public void closeCompany(UUID adminId, String adminUsername, UUID companyId) {
        validateAdmin(adminId, adminUsername);
        companyService.closeCompanyAsAdmin(adminUsername, companyId);
        log(adminId, adminUsername, "CLOSE_COMPANY", companyId.toString());
    }

    public void removeSubscriber(UUID adminId, String adminUsername, String usernameToRemove) {
        validateAdmin(adminId, adminUsername);
        companyService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);
        log(adminId, adminUsername, "REMOVE_SUBSCRIBER_ROLES", usernameToRemove);
    }

    public void sendSystemMessage(UUID adminId, String adminUsername, String message) {
        validateAdmin(adminId, adminUsername);
        userService.adminMessage(adminUsername, message);
        log(adminId, adminUsername, "SEND_SYSTEM_MESSAGE", "all-users");
    }

    public List<PurchaseHistoryDTO> getAllPurchaseHistory(UUID adminId, String adminUsername) {
        validateAdmin(adminId, adminUsername);
        return purchaseService.getAllHistory(adminId);
    }

    public List<PurchaseHistoryDTO> getPurchaseHistoryByUser(UUID adminId, String adminUsername, UUID userId) {
        validateAdmin(adminId, adminUsername);
        return purchaseService.getHistoryByUser(adminId, userId);
    }

    public List<PurchaseHistoryDTO> getPurchaseHistoryByEvent(UUID adminId, String adminUsername, UUID eventId) {
        validateAdmin(adminId, adminUsername);
        return purchaseService.getHistoryByEvent(adminId, eventId);
    }

    public List<PurchaseHistoryDTO> getPurchaseHistoryByCompany(UUID adminId, String adminUsername, UUID companyId) {
        validateAdmin(adminId, adminUsername);
        return purchaseService.getHistoryByCompany(adminId, companyId);
    }

    public AdminComplaintDTO createComplaint(
            UUID reporterUserId,
            String reporterUsername,
            String title,
            String description) {
        AdminComplaint complaint = new AdminComplaint(
                reporterUserId,
                reporterUsername,
                title,
                description
        );

        adminRepository.saveComplaint(complaint);

        return toComplaintDTO(complaint);
    }

    public List<AdminComplaintDTO> getAllComplaints(UUID adminId, String adminUsername) {
        validateAdmin(adminId, adminUsername);

        return adminRepository.getAllComplaints()
                .stream()
                .map(this::toComplaintDTO)
                .toList();
    }

    public AdminComplaintDTO respondToComplaint(
            UUID adminId,
            String adminUsername,
            UUID complaintId,
            String response) {
        validateAdmin(adminId, adminUsername);

        AdminComplaint complaint = adminRepository.findComplaintById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found"));

        complaint.respond(adminUsername, response);
        adminRepository.saveComplaint(complaint);

        log(adminId, adminUsername, "RESPOND_TO_COMPLAINT", complaintId.toString());

        return toComplaintDTO(complaint);
    }

    public List<AdminQueueSnapshotDTO> getAllQueues(UUID adminId, String adminUsername) {
        validateAdmin(adminId, adminUsername);

        return queueManager.getAllQueueSnapshots()
                .stream()
                .map(this::toQueueSnapshotDTO)
                .toList();
    }

    public AdminQueueSnapshotDTO getQueue(UUID adminId, String adminUsername, UUID eventId) {
        validateAdmin(adminId, adminUsername);
        return toQueueSnapshotDTO(queueManager.getQueueSnapshot(eventId));
    }

    public AdminQueueSnapshotDTO releaseQueueBatch(
            UUID adminId,
            String adminUsername,
            UUID eventId,
            int batchSize) {
        validateAdmin(adminId, adminUsername);

        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }

        queueManager.releaseBatch(eventId, batchSize);

        log(adminId, adminUsername, "RELEASE_QUEUE_BATCH", eventId.toString());

        return toQueueSnapshotDTO(queueManager.getQueueSnapshot(eventId));
    }

    public AdminQueueSnapshotDTO clearQueue(UUID adminId, String adminUsername, UUID eventId) {
        validateAdmin(adminId, adminUsername);

        queueManager.clearQueue(eventId);

        log(adminId, adminUsername, "CLEAR_QUEUE", eventId.toString());

        return toQueueSnapshotDTO(queueManager.getQueueSnapshot(eventId));
    }

    public AdminAnalyticsDTO updateQueueSettings(
            UUID adminId,
            String adminUsername,
            AdminQueueSettingsRequest request) {
        validateAdmin(adminId, adminUsername);

        if (request == null) {
            throw new IllegalArgumentException("Queue settings request is required");
        }

        if (request.maxConcurrentSelectors != null) {
            if (request.maxConcurrentSelectors <= 0) {
                throw new IllegalArgumentException("Max concurrent selectors must be positive");
            }

            queueManager.setMaxConcurrentSelectors(request.maxConcurrentSelectors);
        }

        if (request.minutesToStartSelection != null) {
            if (request.minutesToStartSelection <= 0) {
                throw new IllegalArgumentException("Minutes to start selection must be positive");
            }

            queueManager.setHowManyMinutesToStartSelection(request.minutesToStartSelection);
        }

        log(adminId, adminUsername, "UPDATE_QUEUE_SETTINGS", "global");

        return getAnalytics(adminId, adminUsername);
    }

    public AdminAnalyticsDTO getAnalytics(UUID adminId, String adminUsername) {
        validateAdmin(adminId, adminUsername);

        int registeredUsersCount = userRepository.getAllUsers().size();
        int loggedInUsersCount = (int) userRepository.getAllUsers()
                .values()
                .stream()
                .filter(user -> user.getStatus() == UserStatus.LOGGED_IN)
                .count();

        int activeCompaniesCount = companyRepository.getAllActive().size();
        int activeQueuesCount = queueManager.getAllQueueSnapshots().size();
        int activePurchasesCount = purchaseRepository.findAll().size();
        int totalPurchasesCount = historyRepository.getAll().size();

        SystemAnalyticsSnapshot snapshot = new SystemAnalyticsSnapshot(
                registeredUsersCount,
                loggedInUsersCount,
                activeCompaniesCount,
                activeQueuesCount,
                activePurchasesCount,
                totalPurchasesCount
        );

        adminRepository.saveAnalyticsSnapshot(snapshot);

        AdminAnalyticsDTO dto = new AdminAnalyticsDTO();
        dto.registeredUsersCount = snapshot.getRegisteredUsersCount();
        dto.loggedInUsersCount = snapshot.getLoggedInUsersCount();
        dto.activeCompaniesCount = snapshot.getActiveCompaniesCount();
        dto.activeQueuesCount = snapshot.getActiveQueuesCount();
        dto.activePurchasesCount = snapshot.getActivePurchasesCount();
        dto.totalPurchasesCount = snapshot.getTotalPurchasesCount();
        dto.createdAt = snapshot.getCreatedAt();

        return dto;
    }

    private void validateAdmin(UUID adminId, String adminUsername) {
        if (adminId == null) {
            throw new IllegalArgumentException("Admin ID is required");
        }

        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalArgumentException("Admin username is required");
        }

        if (!userRepository.existsAdmin(adminId) || !userRepository.isSystemAdmin(adminUsername)) {
            throw new IllegalArgumentException("User is not system admin");
        }
    }

    private void log(UUID adminId, String adminUsername, String action, String target) {
        adminRepository.saveActionLog(new AdminActionLog(adminId, adminUsername, action, target));
    }

    private AdminComplaintDTO toComplaintDTO(AdminComplaint complaint) {
        AdminComplaintDTO dto = new AdminComplaintDTO();
        dto.id = complaint.getId();
        dto.reporterUserId = complaint.getReporterUserId();
        dto.reporterUsername = complaint.getReporterUsername();
        dto.title = complaint.getTitle();
        dto.description = complaint.getDescription();
        dto.status = complaint.getStatus().toString();
        dto.adminResponse = complaint.getAdminResponse();
        dto.responderAdminUsername = complaint.getResponderAdminUsername();
        dto.createdAt = complaint.getCreatedAt();
        dto.respondedAt = complaint.getRespondedAt();
        return dto;
    }

    private AdminQueueSnapshotDTO toQueueSnapshotDTO(QueueManager.QueueSnapshot snapshot) {
        AdminQueueSnapshotDTO dto = new AdminQueueSnapshotDTO();
        dto.eventId = snapshot.eventId();
        dto.queueSize = snapshot.queueSize();
        dto.activeSelectorsCount = snapshot.activeSelectorsCount();
        dto.maxConcurrentSelectors = snapshot.maxConcurrentSelectors();
        dto.minutesToStartSelection = snapshot.minutesToStartSelection();
        return dto;
    }
}