package org.example.DomainLayer.Events;

import java.util.UUID;

public class PurchaseCompletedEvent implements IDomainEvent {
    private final UUID userId;

    public PurchaseCompletedEvent(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
    
}
