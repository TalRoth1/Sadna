package org.example.DomainLayer.Events;

public class LotteryWonEvent implements IDomainEvent {
    private final String userId;

    public LotteryWonEvent(String userId) {
        this.userId = userId;

    }

    public String getUserId() {
        return userId;
    }

}
