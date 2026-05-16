package org.example.DomainLayer.Events;

public class PurchaseCompletedEvent implements IDomainEvent {
    private final String userId;

    public PurchaseCompletedEvent(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
    
}
