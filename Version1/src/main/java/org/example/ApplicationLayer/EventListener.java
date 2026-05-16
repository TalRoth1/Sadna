package org.example.ApplicationLayer;

import org.example.DomainLayer.Events.IDomainEvent;
import org.example.DomainLayer.Events.LotteryWonEvent;
import org.example.DomainLayer.Events.PurchaseCompletedEvent;

public class EventListener {

    private final NotificationService notificationService;

    public EventListener(NotificationService notificationService) {
        if (notificationService == null) {
            throw new IllegalArgumentException("Notification service is required");
        }

        this.notificationService = notificationService;
    }

    public void handle(IDomainEvent event) {
        if (event instanceof LotteryWonEvent lotteryWonEvent) {
            notificationService.notifyUser(
                    lotteryWonEvent.getUserId(),
                    "Congratulations! You won the lottery!"
            );
        }

        if (event instanceof PurchaseCompletedEvent purchaseCompletedEvent) {
            notificationService.notifyUser(
                    purchaseCompletedEvent.getUserId().toString(),
                    "Your purchase was completed successfully."
            );
        }
    }
}