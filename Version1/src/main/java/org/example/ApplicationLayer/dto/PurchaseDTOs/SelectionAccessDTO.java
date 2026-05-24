package org.example.ApplicationLayer.dto.PurchaseDTOs;

import java.time.LocalDateTime;
import java.util.UUID;

public class SelectionAccessDTO {
    public UUID eventId;
    public UUID userId;
    public boolean allowed;
    public int positionInQueue;
    public int queueSize;
    public LocalDateTime accessExpiresAt;
    public String message;

    public SelectionAccessDTO() {
    }

    public SelectionAccessDTO(
            UUID eventId,
            UUID userId,
            boolean allowed,
            int positionInQueue,
            int queueSize,
            LocalDateTime accessExpiresAt,
            String message
    ) {
        this.eventId = eventId;
        this.userId = userId;
        this.allowed = allowed;
        this.positionInQueue = positionInQueue;
        this.queueSize = queueSize;
        this.accessExpiresAt = accessExpiresAt;
        this.message = message;
    }
}
