package org.example.ApplicationLayer.dto.AdminDTOs;

import java.time.LocalDateTime;
import java.util.UUID;

public class AdminComplaintDTO {
    public UUID id;
    public UUID reporterUserId;
    public String reporterUsername;
    public String title;
    public String description;
    public String status;
    public String adminResponse;
    public String responderAdminUsername;
    public LocalDateTime createdAt;
    public LocalDateTime respondedAt;
}