package org.example.ApplicationLayer;

import org.example.DomainLayer.NotificationAggregate.INotifier;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class NotificationServiceTest {

    private NotificationService createSuccessfulNotificationService() {
        INotifier notifier = (userId, message) -> true;
        return new NotificationService(notifier);
    }

    private NotificationService createUnsuccessfulNotificationService() {
        INotifier notifier = (userId, message) -> false;
        return new NotificationService(notifier);
    }

    @Test
    public void SuccessfulNotificationSending() {
        NotificationService service = createSuccessfulNotificationService();

        boolean result = service.notifyUser("user-1", "New notification");

        assertTrue(result);
    }

    @Test
    public void NotificationSendingWithoutListenersShouldReturnFalse() {
        NotificationService service = createUnsuccessfulNotificationService();

        boolean result = service.notifyUser("user-1", "New notification");

        assertFalse(result);
    }

    @Test
    public void NotificationSendingWithNullUserIdShouldFail() {
        NotificationService service = createSuccessfulNotificationService();

        assertThrows(IllegalArgumentException.class, () ->
                service.notifyUser(null, "New notification")
        );
    }

    @Test
    public void NotificationSendingWithNullMessageShouldFail() {
        NotificationService service = createSuccessfulNotificationService();

        assertThrows(IllegalArgumentException.class, () ->
                service.notifyUser("user-1", null)
        );
    }

    @Test
    public void NotificationSendingWithBlankMessageShouldFail() {
        NotificationService service = createSuccessfulNotificationService();

        assertThrows(IllegalArgumentException.class, () ->
                service.notifyUser("user-1", "   ")
        );
    }

    @Test
    public void NotificationServiceWithoutNotifierShouldFail() {
        assertThrows(IllegalArgumentException.class, () ->
                new NotificationService(null)
        );
    }
}