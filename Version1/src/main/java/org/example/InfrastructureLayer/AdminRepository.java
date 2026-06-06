package org.example.InfrastructureLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.example.DomainLayer.IAdminRepository;
import org.example.DomainLayer.AdminAggregate.AdminActionLog;
import org.example.DomainLayer.AdminAggregate.AdminComplaint;
import org.example.DomainLayer.AdminAggregate.AdminComplaintStatus;
import org.example.DomainLayer.AdminAggregate.SystemAnalyticsSnapshot;
import org.springframework.stereotype.Repository;

public class AdminRepository implements IAdminRepository {
    private final ConcurrentHashMap<UUID, AdminComplaint> complaints = new ConcurrentHashMap<>();
    private final List<AdminActionLog> actionLogs = new ArrayList<>();
    private final List<SystemAnalyticsSnapshot> analyticsSnapshots = new ArrayList<>();

    @Override
    public void saveComplaint(AdminComplaint complaint) {
        if (complaint == null) {
            throw new IllegalArgumentException("Complaint is required");
        }

        complaints.put(complaint.getId(), complaint);
    }

    @Override
    public Optional<AdminComplaint> findComplaintById(UUID complaintId) {
        if (complaintId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(complaints.get(complaintId));
    }

    @Override
    public List<AdminComplaint> getAllComplaints() {
        return new ArrayList<>(complaints.values());
    }

    @Override
    public List<AdminComplaint> getOpenComplaints() {
        return complaints.values()
                .stream()
                .filter(complaint -> complaint.getStatus() == AdminComplaintStatus.OPEN)
                .toList();
    }

    @Override
    public void saveActionLog(AdminActionLog actionLog) {
        if (actionLog == null) {
            throw new IllegalArgumentException("Action log is required");
        }

        actionLogs.add(actionLog);
    }

    @Override
    public List<AdminActionLog> getActionLogs() {
        return new ArrayList<>(actionLogs);
    }

    @Override
    public void saveAnalyticsSnapshot(SystemAnalyticsSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Analytics snapshot is required");
        }

        analyticsSnapshots.add(snapshot);
    }

    @Override
    public List<SystemAnalyticsSnapshot> getAnalyticsSnapshots() {
        return new ArrayList<>(analyticsSnapshots);
    }
}