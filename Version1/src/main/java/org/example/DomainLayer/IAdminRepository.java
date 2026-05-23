package org.example.DomainLayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.example.DomainLayer.AdminAggregate.AdminActionLog;
import org.example.DomainLayer.AdminAggregate.AdminComplaint;
import org.example.DomainLayer.AdminAggregate.SystemAnalyticsSnapshot;

public interface IAdminRepository {
    void saveComplaint(AdminComplaint complaint);

    Optional<AdminComplaint> findComplaintById(UUID complaintId);

    List<AdminComplaint> getAllComplaints();

    List<AdminComplaint> getOpenComplaints();

    void saveActionLog(AdminActionLog actionLog);

    List<AdminActionLog> getActionLogs();

    void saveAnalyticsSnapshot(SystemAnalyticsSnapshot snapshot);

    List<SystemAnalyticsSnapshot> getAnalyticsSnapshots();
}