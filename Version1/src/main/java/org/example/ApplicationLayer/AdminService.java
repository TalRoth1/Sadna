package org.example.ApplicationLayer;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.example.ApplicationLayer.dto.AdminDTOs.AdminAnalyticsDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminCompanyDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminComplaintDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminQueueSettingsRequest;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminQueueSnapshotDTO;
import org.example.ApplicationLayer.dto.AdminDTOs.AdminSubscriberDTO;
import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;
import org.example.DomainLayer.IAdminRepository;
import org.example.DomainLayer.ICompanyRepository;
import org.example.DomainLayer.IHistoryRepository;
import org.example.DomainLayer.IPurchaseRepository;
import org.example.DomainLayer.IUserRepository;
import org.example.DomainLayer.PurchaseDomainService;
import org.example.DomainLayer.RolesDomainService;
import org.example.DomainLayer.AdminAggregate.AdminActionLog;
import org.example.DomainLayer.AdminAggregate.AdminComplaint;
import org.example.DomainLayer.AdminAggregate.SystemAnalyticsSnapshot;
import org.example.DomainLayer.CompanyAggregate.Company;
import org.example.DomainLayer.EventAggregate.Event;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.example.DomainLayer.PurchaseHistoryAggregate.PurchaseHistory;
import org.example.DomainLayer.UserAggregate.CompanyFounder;
import org.example.DomainLayer.UserAggregate.CompanyManager;
import org.example.DomainLayer.UserAggregate.CompanyOwner;
import org.example.DomainLayer.UserAggregate.ICompanyMember;
import org.example.DomainLayer.UserAggregate.User;
import org.example.DomainLayer.UserAggregate.UserStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
    private static final Logger logger = Logger.getLogger(AdminService.class.getName());

    private final IUserRepository userRepository;
    private final ICompanyRepository companyRepository;
    private final IPurchaseRepository purchaseRepository;
    private final IHistoryRepository historyRepository;
    private final IAdminRepository adminRepository;

    private final RolesDomainService rolesDomainService;
    private final PurchaseDomainService purchaseDomainService;
    private final QueueManager queueManager;
    private final INotifier notifier;

    public AdminService(
            IUserRepository userRepository,
            ICompanyRepository companyRepository,
            IPurchaseRepository purchaseRepository,
            IHistoryRepository historyRepository,
            IAdminRepository adminRepository,
            RolesDomainService rolesDomainService,
            PurchaseDomainService purchaseDomainService,
            QueueManager queueManager,
            INotifier notifier) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.purchaseRepository = purchaseRepository;
        this.historyRepository = historyRepository;
        this.adminRepository = adminRepository;
        this.rolesDomainService = rolesDomainService;
        this.purchaseDomainService = purchaseDomainService;
        this.queueManager = queueManager;
        this.notifier = notifier;
    }

    public void closeCompany(UUID adminId, String adminUsername, UUID companyId) {
        String action = "CLOSE_COMPANY";
        logInfo(adminId, adminUsername, action, "Started. companyId=" + companyId);

        try {
            validateAdmin(adminId, adminUsername);

            if (companyId == null) {
                throw new IllegalArgumentException("Company ID is required");
            }

            Company company = companyRepository.findByID(companyId)
                    .orElseThrow(() -> new IllegalArgumentException("Company not found"));

            if (!company.isActive()) {
                throw new IllegalStateException("Company is already closed");
            }

            String founderUsername = company.getFounderUsername();

            rolesDomainService.closeCompanyAsAdmin(adminUsername, companyId);

            if (founderUsername != null && !founderUsername.isBlank()) {
                notifier.notifyUser(
                        founderUsername,
                        "Company " + company.getName() + " has been closed by system admin."
                );
            }

            saveActionLog(adminId, adminUsername, action, companyId.toString());
            logInfo(adminId, adminUsername, action, "Completed successfully. companyId=" + companyId);
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed. companyId=" + companyId, e);
            throw e;
        }
    }

    public void removeSubscriber(UUID adminId, String adminUsername, String usernameToRemove) {
        String action = "REMOVE_SUBSCRIBER_ROLES";
        logInfo(adminId, adminUsername, action, "Started. usernameToRemove=" + usernameToRemove);

        try {
            validateAdmin(adminId, adminUsername);

            if (usernameToRemove == null || usernameToRemove.isBlank()) {
                throw new IllegalArgumentException("Username to remove is required");
            }

            rolesDomainService.removeCompanyMemberAsAdmin(adminUsername, usernameToRemove);

            notifier.notifyUser(
                    usernameToRemove,
                    "Your company roles were removed by system admin."
            );

            saveActionLog(adminId, adminUsername, action, usernameToRemove);
            logInfo(adminId, adminUsername, action, "Completed successfully. usernameToRemove=" + usernameToRemove);
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed. usernameToRemove=" + usernameToRemove, e);
            throw e;
        }
    }

    public void sendSystemMessage(UUID adminId, String adminUsername, String message) {
        String action = "SEND_SYSTEM_MESSAGE";
        logInfo(adminId, adminUsername, action, "Started.");

        try {
            validateAdmin(adminId, adminUsername);

            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("System message is required");
            }

            int notifiedUsersCount = 0;

            for (User user : userRepository.getAllUsers().values()) {
                notifier.notifyUser(user.getId(), message);
                notifiedUsersCount++;
            }

            saveActionLog(adminId, adminUsername, action, "all-users");
            logInfo(adminId, adminUsername, action, "Completed successfully. notifiedUsersCount=" + notifiedUsersCount);
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed.", e);
            throw e;
        }
    }

    public List<AdminCompanyDTO> getCompanies(UUID adminId, String adminUsername) {
        String action = "GET_COMPANIES";
        logInfo(adminId, adminUsername, action, "Started.");

        try {
            validateAdmin(adminId, adminUsername);

            List<AdminCompanyDTO> result = companyRepository.getAll()
                    .stream()
                    .map(this::toCompanyDTO)
                    .toList();

            logInfo(adminId, adminUsername, action, "Completed successfully. resultSize=" + result.size());
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed.", e);
            throw e;
        }
    }

    public List<AdminSubscriberDTO> getSubscribers(UUID adminId, String adminUsername) {
        String action = "GET_SUBSCRIBERS";
        logInfo(adminId, adminUsername, action, "Started.");

        try {
            validateAdmin(adminId, adminUsername);

            List<AdminSubscriberDTO> result = userRepository.getAllUsers()
                    .values()
                    .stream()
                    .map(this::toSubscriberDTO)
                    .toList();

            logInfo(adminId, adminUsername, action, "Completed successfully. resultSize=" + result.size());
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed.", e);
            throw e;
        }
    }

    public List<PurchaseHistoryDTO> getAllPurchaseHistory(UUID adminId, String adminUsername) {
        String action = "GET_ALL_PURCHASE_HISTORY";
        logInfo(adminId, adminUsername, action, "Started.");

        try {
            validateAdmin(adminId, adminUsername);

            List<PurchaseHistoryDTO> result = toPurchaseHistoryDTOs(
                    purchaseDomainService.getAllHistory()
            );

            logInfo(adminId, adminUsername, action, "Completed successfully. resultSize=" + result.size());
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed.", e);
            throw e;
        }
    }

    public List<PurchaseHistoryDTO> getPurchaseHistoryByUser(
            UUID adminId,
            String adminUsername,
            UUID userId) {
        String action = "GET_PURCHASE_HISTORY_BY_USER";
        logInfo(adminId, adminUsername, action, "Started. userId=" + userId);

        try {
            validateAdmin(adminId, adminUsername);

            if (userId == null) {
                throw new IllegalArgumentException("User ID is required");
            }

            List<PurchaseHistoryDTO> result = toPurchaseHistoryDTOs(
                    purchaseDomainService.getHistoryByUser(userId)
            );

            logInfo(adminId, adminUsername, action, "Completed successfully. userId=" + userId + ", resultSize=" + result.size());
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed. userId=" + userId, e);
            throw e;
        }
    }

    public List<PurchaseHistoryDTO> getPurchaseHistoryByEvent(
            UUID adminId,
            String adminUsername,
            UUID eventId) {
        String action = "GET_PURCHASE_HISTORY_BY_EVENT";
        logInfo(adminId, adminUsername, action, "Started. eventId=" + eventId);

        try {
            validateAdmin(adminId, adminUsername);

            if (eventId == null) {
                throw new IllegalArgumentException("Event ID is required");
            }

            List<PurchaseHistoryDTO> result = toPurchaseHistoryDTOs(
                    purchaseDomainService.getHistoryByEvent(eventId)
            );

            logInfo(adminId, adminUsername, action, "Completed successfully. eventId=" + eventId + ", resultSize=" + result.size());
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed. eventId=" + eventId, e);
            throw e;
        }
    }

    public List<PurchaseHistoryDTO> getPurchaseHistoryByCompany(
            UUID adminId,
            String adminUsername,
            UUID companyId) {
        String action = "GET_PURCHASE_HISTORY_BY_COMPANY";
        logInfo(adminId, adminUsername, action, "Started. companyId=" + companyId);

        try {
            validateAdmin(adminId, adminUsername);

            if (companyId == null) {
                throw new IllegalArgumentException("Company ID is required");
            }

            List<PurchaseHistoryDTO> result = toPurchaseHistoryDTOs(
                    purchaseDomainService.getHistoryByCompany(companyId)
            );

            logInfo(adminId, adminUsername, action, "Completed successfully. companyId=" + companyId + ", resultSize=" + result.size());
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed. companyId=" + companyId, e);
            throw e;
        }
    }

    public List<PurchaseHistoryDTO> getPurchaseHistoryByFilter(
            UUID adminId,
            String adminUsername,
            String filterType,
            UUID filterId) {
        String action = "GET_PURCHASE_HISTORY_BY_FILTER";
        logInfo(adminId, adminUsername, action, "Started. filterType=" + filterType + ", filterId=" + filterId);

        try {
            validateAdmin(adminId, adminUsername);

            if (filterType == null || filterType.isBlank()) {
                throw new IllegalArgumentException("Filter type is required");
            }

            List<PurchaseHistoryDTO> result = switch (filterType.toLowerCase()) {
                case "all" -> getAllPurchaseHistory(adminId, adminUsername);
                case "user" -> getPurchaseHistoryByUser(adminId, adminUsername, filterId);
                case "event" -> getPurchaseHistoryByEvent(adminId, adminUsername, filterId);
                case "company" -> getPurchaseHistoryByCompany(adminId, adminUsername, filterId);
                default -> throw new IllegalArgumentException("Invalid filter type");
            };

            logInfo(adminId, adminUsername, action, "Completed successfully. filterType=" + filterType + ", resultSize=" + result.size());
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed. filterType=" + filterType + ", filterId=" + filterId, e);
            throw e;
        }
    }

    public AdminComplaintDTO createComplaint(
            UUID reporterUserId,
            String reporterUsername,
            String title,
            String description) {
        String action = "CREATE_COMPLAINT";
        logInfo(reporterUserId, reporterUsername, action, "Started.");

        try {
            if (reporterUserId == null) {
                throw new IllegalArgumentException("Reporter user ID is required");
            }

            if (reporterUsername == null || reporterUsername.isBlank()) {
                throw new IllegalArgumentException("Reporter username is required");
            }

            AdminComplaint complaint = new AdminComplaint(
                    reporterUserId,
                    reporterUsername,
                    title,
                    description
            );

            adminRepository.saveComplaint(complaint);

            AdminComplaintDTO result = toComplaintDTO(complaint);

            logInfo(reporterUserId, reporterUsername, action, "Completed successfully. complaintId=" + result.id);
            return result;
        } catch (RuntimeException e) {
            logError(reporterUserId, reporterUsername, action, "Failed.", e);
            throw e;
        }
    }

    public List<AdminComplaintDTO> getAllComplaints(UUID adminId, String adminUsername) {
        String action = "GET_ALL_COMPLAINTS";
        logInfo(adminId, adminUsername, action, "Started.");

        try {
            validateAdmin(adminId, adminUsername);

            List<AdminComplaintDTO> result = adminRepository.getAllComplaints()
                    .stream()
                    .map(this::toComplaintDTO)
                    .toList();

            logInfo(adminId, adminUsername, action, "Completed successfully. resultSize=" + result.size());
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed.", e);
            throw e;
        }
    }

    public List<AdminComplaintDTO> getOpenComplaints(UUID adminId, String adminUsername) {
        String action = "GET_OPEN_COMPLAINTS";
        logInfo(adminId, adminUsername, action, "Started.");

        try {
            validateAdmin(adminId, adminUsername);

            List<AdminComplaintDTO> result = adminRepository.getOpenComplaints()
                    .stream()
                    .map(this::toComplaintDTO)
                    .toList();

            logInfo(adminId, adminUsername, action, "Completed successfully. resultSize=" + result.size());
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed.", e);
            throw e;
        }
    }

    public AdminComplaintDTO respondToComplaint(
            UUID adminId,
            String adminUsername,
            UUID complaintId,
            String response) {
        String action = "RESPOND_TO_COMPLAINT";
        logInfo(adminId, adminUsername, action, "Started. complaintId=" + complaintId);

        try {
            validateAdmin(adminId, adminUsername);

            if (complaintId == null) {
                throw new IllegalArgumentException("Complaint ID is required");
            }

            AdminComplaint complaint = adminRepository.findComplaintById(complaintId)
                    .orElseThrow(() -> new IllegalArgumentException("Complaint not found"));

            complaint.respond(adminUsername, response);
            adminRepository.saveComplaint(complaint);

            notifier.notifyUser(
                    complaint.getReporterUserId(),
                    "Your complaint was answered by system admin."
            );

            saveActionLog(adminId, adminUsername, action, complaintId.toString());

            AdminComplaintDTO result = toComplaintDTO(complaint);

            logInfo(adminId, adminUsername, action, "Completed successfully. complaintId=" + complaintId);
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed. complaintId=" + complaintId, e);
            throw e;
        }
    }

    public AdminComplaintDTO closeComplaint(
            UUID adminId,
            String adminUsername,
            UUID complaintId) {
        String action = "CLOSE_COMPLAINT";
        logInfo(adminId, adminUsername, action, "Started. complaintId=" + complaintId);

        try {
            validateAdmin(adminId, adminUsername);

            if (complaintId == null) {
                throw new IllegalArgumentException("Complaint ID is required");
            }

            AdminComplaint complaint = adminRepository.findComplaintById(complaintId)
                    .orElseThrow(() -> new IllegalArgumentException("Complaint not found"));

            complaint.close();
            adminRepository.saveComplaint(complaint);

            saveActionLog(adminId, adminUsername, action, complaintId.toString());

            AdminComplaintDTO result = toComplaintDTO(complaint);

            logInfo(adminId, adminUsername, action, "Completed successfully. complaintId=" + complaintId);
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed. complaintId=" + complaintId, e);
            throw e;
        }
    }

    public List<AdminQueueSnapshotDTO> getAllQueues(UUID adminId, String adminUsername) {
        String action = "GET_ALL_QUEUES";
        logInfo(adminId, adminUsername, action, "Started.");

        try {
            validateAdmin(adminId, adminUsername);

            List<AdminQueueSnapshotDTO> result = queueManager.getAllQueueSnapshots()
                    .stream()
                    .map(this::toQueueSnapshotDTO)
                    .toList();

            logInfo(adminId, adminUsername, action, "Completed successfully. resultSize=" + result.size());
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed.", e);
            throw e;
        }
    }

    public AdminQueueSnapshotDTO getQueue(UUID adminId, String adminUsername, UUID eventId) {
        String action = "GET_QUEUE";
        logInfo(adminId, adminUsername, action, "Started. eventId=" + eventId);

        try {
            validateAdmin(adminId, adminUsername);

            if (eventId == null) {
                throw new IllegalArgumentException("Event ID is required");
            }

            AdminQueueSnapshotDTO result = toQueueSnapshotDTO(queueManager.getQueueSnapshot(eventId));

            logInfo(adminId, adminUsername, action, "Completed successfully. eventId=" + eventId);
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed. eventId=" + eventId, e);
            throw e;
        }
    }

    public AdminQueueSnapshotDTO releaseQueueBatch(
            UUID adminId,
            String adminUsername,
            UUID eventId,
            int batchSize) {
        String action = "RELEASE_QUEUE_BATCH";
        logInfo(adminId, adminUsername, action, "Started. eventId=" + eventId + ", batchSize=" + batchSize);

        try {
            validateAdmin(adminId, adminUsername);

            if (eventId == null) {
                throw new IllegalArgumentException("Event ID is required");
            }

            if (batchSize <= 0) {
                throw new IllegalArgumentException("Batch size must be positive");
            }

            queueManager.releaseBatch(eventId, batchSize);

            saveActionLog(adminId, adminUsername, action, eventId.toString());

            AdminQueueSnapshotDTO result = toQueueSnapshotDTO(queueManager.getQueueSnapshot(eventId));

            logInfo(adminId, adminUsername, action, "Completed successfully. eventId=" + eventId);
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed. eventId=" + eventId + ", batchSize=" + batchSize, e);
            throw e;
        }
    }

    public AdminQueueSnapshotDTO clearQueue(UUID adminId, String adminUsername, UUID eventId) {
        String action = "CLEAR_QUEUE";
        logInfo(adminId, adminUsername, action, "Started. eventId=" + eventId);

        try {
            validateAdmin(adminId, adminUsername);

            if (eventId == null) {
                throw new IllegalArgumentException("Event ID is required");
            }

            queueManager.clearQueue(eventId);

            saveActionLog(adminId, adminUsername, action, eventId.toString());

            AdminQueueSnapshotDTO result = toQueueSnapshotDTO(queueManager.getQueueSnapshot(eventId));

            logInfo(adminId, adminUsername, action, "Completed successfully. eventId=" + eventId);
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed. eventId=" + eventId, e);
            throw e;
        }
    }

    public AdminAnalyticsDTO updateQueueSettings(
            UUID adminId,
            String adminUsername,
            AdminQueueSettingsRequest request) {
        String action = "UPDATE_QUEUE_SETTINGS";
        logInfo(adminId, adminUsername, action, "Started.");

        try {
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

            saveActionLog(adminId, adminUsername, action, "global");

            AdminAnalyticsDTO result = getAnalytics(adminId, adminUsername);

            logInfo(adminId, adminUsername, action, "Completed successfully.");
            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed.", e);
            throw e;
        }
    }

    public AdminAnalyticsDTO getAnalytics(UUID adminId, String adminUsername) {
        String action = "GET_ANALYTICS";
        logInfo(adminId, adminUsername, action, "Started.");

        try {
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

            AdminAnalyticsDTO result = toAnalyticsDTO(snapshot);

            logInfo(adminId, adminUsername, action,
                    "Completed successfully. registeredUsersCount=" + registeredUsersCount
                            + ", loggedInUsersCount=" + loggedInUsersCount
                            + ", activeCompaniesCount=" + activeCompaniesCount
                            + ", activeQueuesCount=" + activeQueuesCount
                            + ", activePurchasesCount=" + activePurchasesCount
                            + ", totalPurchasesCount=" + totalPurchasesCount);

            return result;
        } catch (RuntimeException e) {
            logError(adminId, adminUsername, action, "Failed.", e);
            throw e;
        }
    }

    private void validateAdmin(UUID adminId, String adminUsername) {
        String action = "VALIDATE_ADMIN";
        logInfo(adminId, adminUsername, action, "Started.");

        if (adminId == null) {
            logger.severe("adminId=null, admin=" + adminUsername
                    + ", action=" + action
                    + ", status=ERROR, message=Admin ID is required");
            throw new IllegalArgumentException("Admin ID is required");
        }

        if (adminUsername == null || adminUsername.isBlank()) {
            logger.severe("adminId=" + adminId
                    + ", admin=" + adminUsername
                    + ", action=" + action
                    + ", status=ERROR, message=Admin username is required");
            throw new IllegalArgumentException("Admin username is required");
        }

        if (!userRepository.existsAdmin(adminId)) {
            logger.severe("adminId=" + adminId
                    + ", admin=" + adminUsername
                    + ", action=" + action
                    + ", status=ERROR, message=Admin ID was not found in admins repository");
            throw new IllegalArgumentException("User is not system admin");
        }

        if (!userRepository.isSystemAdmin(adminUsername)) {
            logger.severe("adminId=" + adminId
                    + ", admin=" + adminUsername
                    + ", action=" + action
                    + ", status=ERROR, message=Admin username was not found in admins repository");
            throw new IllegalArgumentException("User is not system admin");
        }

        logInfo(adminId, adminUsername, action, "Completed successfully.");
    }

    private void saveActionLog(UUID adminId, String adminUsername, String action, String target) {
        adminRepository.saveActionLog(
                new AdminActionLog(adminId, adminUsername, action, target)
        );
    }

    private void logInfo(UUID adminId, String adminUsername, String action, String message) {
        logger.info("adminId=" + adminId
                + ", admin=" + adminUsername
                + ", action=" + action
                + ", status=INFO, message=" + message);
    }

    private void logError(UUID adminId, String adminUsername, String action, String message, RuntimeException e) {
        logger.severe("adminId=" + adminId
                + ", admin=" + adminUsername
                + ", action=" + action
                + ", status=ERROR, message=" + message
                + ", error=" + e.getMessage());
    }

    private AdminCompanyDTO toCompanyDTO(Company company) {
        AdminCompanyDTO dto = new AdminCompanyDTO();
        dto.id = company.getId();
        dto.name = company.getName();
        dto.status = company.isActive() ? "active" : "closed";

        int ownersCount = 0;
        int managersCount = 0;

        for (User user : userRepository.getAllUsers().values()) {
            ICompanyMember role = user.getCompanyRole(company.getId());

            if (role instanceof CompanyFounder || role instanceof CompanyOwner) {
                ownersCount++;
            }

            if (role instanceof CompanyManager) {
                managersCount++;
            }
        }

        dto.ownersCount = ownersCount;
        dto.managersCount = managersCount;

        return dto;
    }

    private AdminSubscriberDTO toSubscriberDTO(User user) {
        AdminSubscriberDTO dto = new AdminSubscriberDTO();
        dto.id = user.getId();
        dto.username = user.getUsername();
        dto.email = user.getEmail();
        dto.status = user.getStatus().toString();
        dto.role = user.getRole().toString();

        return dto;
    }

    private PurchaseHistoryDTO toPurchaseHistoryDTO(PurchaseHistory history) {
        PurchaseHistoryDTO dto = new PurchaseHistoryDTO();

        dto.userId = history.getUserId();
        dto.eventId = history.getEventId();
        dto.ticketIds = history.getTicketIds();
        dto.purchaseDate = history.getPurchaseDate();
        dto.ticketsAmount = dto.ticketIds == null ? 0 : dto.ticketIds.size();

        if (history.getPayment() != null) {
            dto.paymentInfo = history.getPayment().toString();
            dto.totalPrice = history.getPayment().getTotal();
        } else {
            dto.paymentInfo = "";
            dto.totalPrice = 0.0;
        }

        populateEventFields(dto, history.getEventId());

        return dto;
    }

    private List<PurchaseHistoryDTO> toPurchaseHistoryDTOs(List<PurchaseHistory> histories) {
        return histories.stream()
                .map(this::toPurchaseHistoryDTO)
                .toList();
    }

    private void populateEventFields(PurchaseHistoryDTO dto, UUID eventId) {
        Event event = purchaseDomainService.findEventById(eventId);

        if (event == null) {
            return;
        }

        dto.eventName = event.getName();
        dto.eventDate = event.getDate();
        dto.eventLocation = event.getLocation();
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

    private AdminAnalyticsDTO toAnalyticsDTO(SystemAnalyticsSnapshot snapshot) {
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
}