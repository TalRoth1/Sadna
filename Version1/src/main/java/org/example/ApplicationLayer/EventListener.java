package org.example.ApplicationLayer;

import org.example.DomainLayer.Events.IDomainEvent;
import org.example.DomainLayer.Events.LotteryWonEvent;
import org.example.DomainLayer.Events.PurchaseCompletedEvent;
import org.example.DomainLayer.NotificationAggregate.NotificationType;

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
            notificationService.createAndNotify(
                    lotteryWonEvent.getUserId(),
                    NotificationType.LOTTERY_WON,
                    "Lottery won",
                    "Congratulations! You won the lottery for event "
                            + lotteryWonEvent.getEventId()
                            + ". Your access code is: "
                            + lotteryWonEvent.getAccessCode()
                            + ". It expires at: "
                            + lotteryWonEvent.getCodeExpiry(),
                    "EVENT",
                    lotteryWonEvent.getEventId()
            );
        }

        if (event instanceof PurchaseCompletedEvent purchaseCompletedEvent) {
            notificationService.createAndNotify(
                    purchaseCompletedEvent.getUserId().toString(),
                    NotificationType.PURCHASE_COMPLETED,
                    "Purchase completed",
                    "Your purchase was completed successfully.",
                    "PURCHASE",
                    null
            );
        }
    }
}