package org.example.DomainLayer.Events;

import java.time.LocalDateTime;
import java.util.UUID;

public class LotteryWonEvent implements IDomainEvent {
    private final String userId;
    private final UUID eventId;
    private final String accessCode;
    private final LocalDateTime codeExpiry;

    public LotteryWonEvent(String userId, UUID eventId, String accessCode, LocalDateTime codeExpiry) {
        this.userId = userId;
        this.eventId = eventId;
        this.accessCode = accessCode;
        this.codeExpiry = codeExpiry;
    }

    public String getUserId() {
        return userId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public LocalDateTime getCodeExpiry() {
        return codeExpiry;
    }
}