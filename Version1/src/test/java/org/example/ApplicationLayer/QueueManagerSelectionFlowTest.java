package org.example.ApplicationLayer;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Queue selection-flow tests.
 *
 * These tests cover the user-visible queue states before ticket selection:
 * 1. When the event is overloaded, a new user is placed in the waiting queue.
 * 2. When there is free selector capacity, a user receives immediate access
 *    and can enter the ticket-selection page.
 * 3. When a waiting user's turn is released, the user receives access and can
 *    enter the ticket-selection page.
 */
public class QueueManagerSelectionFlowTest {

    private QueueManager queueManager;
    private UUID eventId;

    @Before
    public void setUp() {
        queueManager = new QueueManager();
        eventId = UUID.randomUUID();
    }

    @Test
    public void requestSelectionAccess_whenSystemIsOverloaded_placesUserInQueue() {
        queueManager.setMaxConcurrentSelectors(1);

        UUID firstUser = UUID.randomUUID();
        UUID queuedUser = UUID.randomUUID();

        QueueAccessResult firstResult = queueManager.requestSelectionAccess(firstUser, eventId);
        QueueAccessResult queuedResult = queueManager.requestSelectionAccess(queuedUser, eventId);

        assertTrue("first user should get immediate selection access", firstResult.isAllowed());
        assertNotNull(firstResult.getAccessExpiresAt());

        assertFalse("second user should be waiting because selector capacity is full", queuedResult.isAllowed());
        assertEquals(1, queuedResult.getUserPositionInQueue());
        assertEquals(1, queuedResult.getQueueSize());
        assertEquals(1, queueManager.getQueueSize(eventId));

        assertThrows(
                "queued user must not be allowed to select tickets before being released",
                IllegalStateException.class,
                () -> queueManager.requireSelectionAccess(queuedUser, eventId)
        );
    }

    @Test
    public void requestSelectionAccess_whenCapacityAvailable_grantsImmediateTicketSelectionAccess() {
        queueManager.setMaxConcurrentSelectors(2);

        UUID userId = UUID.randomUUID();

        QueueAccessResult result = queueManager.requestSelectionAccess(userId, eventId);

        assertTrue("user should not enter the queue when selector capacity is available", result.isAllowed());
        assertNotNull(result.getAccessExpiresAt());
        assertEquals(0, result.getQueueSize());
        assertEquals(0, result.getUserPositionInQueue());
        assertTrue(queueManager.hasSelectAccess(userId, eventId));

        queueManager.requireSelectionAccess(userId, eventId);
    }

    @Test
    public void releaseBatch_whenWaitingUserTurnArrives_grantsTicketSelectionAccess() {
        queueManager.setMaxConcurrentSelectors(1);

        UUID activeUser = UUID.randomUUID();
        UUID waitingUser = UUID.randomUUID();

        QueueAccessResult activeResult = queueManager.requestSelectionAccess(activeUser, eventId);
        QueueAccessResult waitingResult = queueManager.requestSelectionAccess(waitingUser, eventId);

        assertTrue(activeResult.isAllowed());
        assertFalse(waitingResult.isAllowed());
        assertEquals(1, queueManager.getQueueSize(eventId));

        queueManager.finishAccess(activeUser, eventId);
        List<UUID> releasedUsers = queueManager.releaseBatch(eventId, 1);

        assertEquals(1, releasedUsers.size());
        assertEquals(waitingUser, releasedUsers.get(0));
        assertEquals(0, queueManager.getQueueSize(eventId));
        assertTrue(queueManager.hasSelectAccess(waitingUser, eventId));

        QueueAccessResult statusAfterRelease = queueManager.getSelectionAccessStatus(waitingUser, eventId);
        assertTrue(statusAfterRelease.isAllowed());
        assertNotNull(statusAfterRelease.getAccessExpiresAt());

        queueManager.requireSelectionAccess(waitingUser, eventId);
    }
}
