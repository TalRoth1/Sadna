package org.example.InfrastructureLayer.Persistence;

import org.example.DomainLayer.IAdminRepository;
import org.example.DomainLayer.AdminAggregate.AdminActionLog;
import org.example.DomainLayer.AdminAggregate.AdminComplaint;
import org.example.DomainLayer.AdminAggregate.AdminComplaintStatus;
import org.example.DomainLayer.AdminAggregate.SystemAnalyticsSnapshot;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Transactional
public class JpaAdminRepository implements IAdminRepository {

    private final SpringDataAdminComplaintRepository complaintJpa;
    private final SpringDataAdminActionLogRepository actionLogJpa;
    private final SpringDataSystemAnalyticsSnapshotRepository analyticsJpa;

    public JpaAdminRepository(SpringDataAdminComplaintRepository complaintJpa,
                              SpringDataAdminActionLogRepository actionLogJpa,
                              SpringDataSystemAnalyticsSnapshotRepository analyticsJpa) {
        this.complaintJpa = complaintJpa;
        this.actionLogJpa = actionLogJpa;
        this.analyticsJpa = analyticsJpa;
    }

    @Override
    public void saveComplaint(AdminComplaint complaint) {
        if (complaint == null) {
            throw new IllegalArgumentException("Complaint is required");
        }

        complaintJpa.save(toEntity(complaint));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AdminComplaint> findComplaintById(UUID complaintId) {
        if (complaintId == null) {
            return Optional.empty();
        }

        return complaintJpa.findById(complaintId)
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminComplaint> getAllComplaints() {
        return complaintJpa.findAll()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminComplaint> getOpenComplaints() {
        return complaintJpa.findByStatus(AdminComplaintStatus.OPEN)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void saveActionLog(AdminActionLog actionLog) {
        if (actionLog == null) {
            throw new IllegalArgumentException("Action log is required");
        }

        actionLogJpa.save(toEntity(actionLog));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminActionLog> getActionLogs() {
        return actionLogJpa.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void saveAnalyticsSnapshot(SystemAnalyticsSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Analytics snapshot is required");
        }

        analyticsJpa.save(toEntity(snapshot));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SystemAnalyticsSnapshot> getAnalyticsSnapshots() {
        return analyticsJpa.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toDomain)
                .toList();
    }

    private AdminComplaintEntity toEntity(AdminComplaint complaint) {
        return new AdminComplaintEntity(
                complaint.getId(),
                complaint.getReporterUserId(),
                complaint.getReporterUsername(),
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getCreatedAt(),
                complaint.getStatus(),
                complaint.getAdminResponse(),
                complaint.getResponderAdminUsername(),
                complaint.getRespondedAt()
        );
    }

    private AdminComplaint toDomain(AdminComplaintEntity entity) {
        return new AdminComplaint(
                entity.getId(),
                entity.getReporterUserId(),
                entity.getReporterUsername(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getStatus(),
                entity.getAdminResponse(),
                entity.getResponderAdminUsername(),
                entity.getRespondedAt()
        );
    }

    private AdminActionLogEntity toEntity(AdminActionLog log) {
        return new AdminActionLogEntity(
                log.getId(),
                log.getAdminId(),
                log.getAdminUsername(),
                log.getAction(),
                log.getTarget(),
                log.getCreatedAt()
        );
    }

    private AdminActionLog toDomain(AdminActionLogEntity entity) {
        return new AdminActionLog(
                entity.getId(),
                entity.getAdminId(),
                entity.getAdminUsername(),
                entity.getAction(),
                entity.getTarget(),
                entity.getCreatedAt()
        );
    }

    private SystemAnalyticsSnapshotEntity toEntity(SystemAnalyticsSnapshot snapshot) {
        return new SystemAnalyticsSnapshotEntity(
                snapshot.getId(),
                snapshot.getRegisteredUsersCount(),
                snapshot.getLoggedInUsersCount(),
                snapshot.getActiveCompaniesCount(),
                snapshot.getActiveQueuesCount(),
                snapshot.getActivePurchasesCount(),
                snapshot.getTotalPurchasesCount(),
                snapshot.getNewSubscriberRatePerMin(),
                snapshot.getTicketReservationRatePerMin(),
                snapshot.getTicketPurchaseRatePerMin(),
                snapshot.getCreatedAt()
        );
    }

    private SystemAnalyticsSnapshot toDomain(SystemAnalyticsSnapshotEntity entity) {
        return new SystemAnalyticsSnapshot(
                entity.getId(),
                entity.getRegisteredUsersCount(),
                entity.getLoggedInUsersCount(),
                entity.getActiveCompaniesCount(),
                entity.getActiveQueuesCount(),
                entity.getActivePurchasesCount(),
                entity.getTotalPurchasesCount(),
                entity.getNewSubscriberRatePerMin(),
                entity.getTicketReservationRatePerMin(),
                entity.getTicketPurchaseRatePerMin(),
                entity.getCreatedAt()
        );
    }
}