package org.example.AdditionalTests;

import org.example.ApplicationLayer.QueueAccessResult;
import org.example.ApplicationLayer.QueueManager;
import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.junit.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class QueueManagerAdditionalTests {

    @Test
    public void requestStatusReleaseFinishAndSnapshots_coverMainQueueBranches() {
        INotifier notifier = mock(INotifier.class);
        QueueManager manager = new QueueManager(notifier);
        manager.setMaxConcurrentSelectors(1);
        manager.setHowManyMinutesToStartSelection(1);

        UUID eventId = UUID.randomUUID();
        UUID activeUser = UUID.randomUUID();
        UUID waitingUser = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();

        QueueAccessResult firstAccess = manager.requestSelectionAccess(activeUser, eventId);
        assertTrue(firstAccess.isAllowed());
        assertEquals(0, firstAccess.getQueueSize());
        assertNotNull(firstAccess.getAccessExpiresAt());

        QueueAccessResult existingAccess = manager.requestSelectionAccess(activeUser, eventId);
        assertTrue(existingAccess.isAllowed());

        QueueAccessResult waiting = manager.requestSelectionAccess(waitingUser, eventId);
        assertFalse(waiting.isAllowed());
        assertEquals(1, waiting.getUserPositionInQueue());
        assertEquals(1, waiting.getQueueSize());

        QueueAccessResult duplicateWaiting = manager.requestSelectionAccess(waitingUser, eventId);
        assertFalse(duplicateWaiting.isAllowed());
        assertEquals(1, duplicateWaiting.getUserPositionInQueue());
        assertEquals(1, duplicateWaiting.getQueueSize());

        QueueAccessResult notQueued = manager.getSelectionAccessStatus(outsider, eventId);
        assertFalse(notQueued.isAllowed());
        assertEquals(-1, notQueued.getUserPositionInQueue());
        assertEquals(1, notQueued.getQueueSize());

        assertTrue(manager.hasSelectAccess(activeUser, eventId));
        manager.requireSelectionAccess(activeUser, eventId);

        QueueManager.QueueSnapshot beforeRelease = manager.getQueueSnapshot(eventId);
        assertEquals(eventId, beforeRelease.eventId());
        assertEquals(1, beforeRelease.queueSize());
        assertEquals(1, beforeRelease.activeSelectorsCount());
        assertEquals(1, beforeRelease.maxConcurrentSelectors());
        assertEquals(1, beforeRelease.minutesToStartSelection());

        manager.finishAccess(activeUser, eventId);
        assertFalse(manager.hasSelectAccess(activeUser, eventId));
        assertThrows(IllegalStateException.class, () -> manager.requireSelectionAccess(activeUser, eventId));

        List<UUID> released = manager.releaseBatch(eventId, 10);
        assertEquals(List.of(waitingUser), released);
        verify(notifier).notifyUser(eq(waitingUser), contains("Your turn has arrived"));

        assertEquals(0, manager.getQueueSize(eventId));
        assertTrue(manager.hasSelectAccess(waitingUser, eventId));
        assertEquals(1, manager.getAllQueueSnapshots().size());

        manager.clearQueue(eventId);
        assertEquals(0, manager.getQueueSize(eventId));
        assertEquals(0, manager.getQueueSnapshot(eventId).activeSelectorsCount());
        assertTrue(manager.getAllQueueSnapshots().isEmpty());
    }

    @Test
    public void validationAndEmptyQueueBranches_throwOrReturnEmptyResults() {
        QueueManager manager = new QueueManager();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertEquals(10, manager.getMaxConcurrentSelectors());
        assertEquals(10, manager.getHowManyMinutesToStartSelection());

        assertThrows(IllegalArgumentException.class, () -> manager.setMaxConcurrentSelectors(0));
        assertThrows(IllegalArgumentException.class, () -> manager.setHowManyMinutesToStartSelection(0));

        assertThrows(DomainException.class, () -> manager.requestSelectionAccess(null, eventId));
        assertThrows(DomainException.class, () -> manager.requestSelectionAccess(userId, null));
        assertThrows(DomainException.class, () -> manager.getSelectionAccessStatus(null, eventId));
        assertThrows(DomainException.class, () -> manager.requireSelectionAccess(userId, null));
        assertThrows(DomainException.class, () -> manager.getQueueSnapshot(null));
        assertThrows(DomainException.class, () -> manager.clearQueue(null));

        assertEquals(-1, manager.getPositionInQueue(userId, eventId));
        assertEquals(0, manager.getQueueSize(eventId));
        assertEquals(List.of(), manager.releaseBatch(eventId, 1));
        assertThrows(IllegalArgumentException.class, () -> manager.releaseBatch(eventId, 0));
    }

    @Test
    public void expiredAccess_isRemovedAndDoesNotAppearInSnapshots() throws Exception {
        QueueManager manager = new QueueManager();
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertTrue(manager.requestSelectionAccess(userId, eventId).isAllowed());
        forceExpiration(manager, eventId, userId, LocalDateTime.now().minusSeconds(1));

        assertNull(manager.getAccessExpiration(userId, eventId));
        assertFalse(manager.hasSelectAccess(userId, eventId));
        assertTrue(manager.getAllQueueSnapshots().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private void forceExpiration(QueueManager manager,
                                 UUID eventId,
                                 UUID userId,
                                 LocalDateTime expiration) throws Exception {
        Field field = QueueManager.class.getDeclaredField("perEventAllowedUsersToStartSelection");
        field.setAccessible(true);
        Map<UUID, Map<UUID, LocalDateTime>> allowed =
                (Map<UUID, Map<UUID, LocalDateTime>>) field.get(manager);
        allowed.get(eventId).put(userId, expiration);
    }
}
