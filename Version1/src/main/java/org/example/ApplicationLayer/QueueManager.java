package org.example.ApplicationLayer;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.NotificationAggregate.INotifier;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;

public class QueueManager {
    private final Map<UUID, Queue<UUID>> queuePerEvent = new LinkedHashMap<>();
    private final Map<UUID, Map<UUID, LocalDateTime>> perEventAllowedUsersToStartSelection = new LinkedHashMap<>();

    private int maxConcurrentSelectors = 10;
    private int howManyMinutesToStartSelection = 10;

    private static final Logger logger = Logger.getLogger(QueueManager.class.getName());

    private final INotifier notifier;

    public QueueManager() {
        this.notifier = null;
    }

    public QueueManager(INotifier notifier) {
        this.notifier = notifier;
    }

    /**
     * Queue gate entry point.
     *
     * This method is intentionally used BEFORE the user reaches the practical
     * ticket-selection stage. It either grants a temporary selection window or
     * places the user in the event-specific waiting room.
     */
    public synchronized QueueAccessResult requestSelectionAccess(UUID userId, UUID eventId) {
        logger.info("Requesting selection access: userId=" + userId + ", eventId=" + eventId);

        ensureUserExists(userId);
        ensureEventExists(eventId);

        removeUsersThatOutOfTime(eventId);
        promoteWaitingUsers(eventId);

        LocalDateTime existingExpiration = getAccessExpiration(userId, eventId);
        if (existingExpiration != null) {
            logger.info("Access already granted for user " + userId + " on event " + eventId);
            return QueueAccessResult.allowed(existingExpiration);
        }

        Queue<UUID> currentEventQueue = queuePerEvent.computeIfAbsent(eventId, k -> new LinkedList<>());

        if (currentEventQueue.contains(userId)) {
            logger.info("User " + userId + " is already in queue for event " + eventId);
            return QueueAccessResult.waiting(
                    getPositionInQueue(userId, eventId),
                    getQueueSize(eventId)
            );
        }

        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.computeIfAbsent(eventId, id -> new HashMap<>());

        if (allowedForEvent.size() < maxConcurrentSelectors && currentEventQueue.isEmpty()) {
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(howManyMinutesToStartSelection);
            allowedForEvent.put(userId, expiresAt);
            logger.info("Immediate selection access granted to user " + userId + " for event " + eventId
                    + " (Capacity: " + allowedForEvent.size() + "/" + maxConcurrentSelectors + ")");
            return QueueAccessResult.allowed(expiresAt);
        }

        currentEventQueue.add(userId);
        logger.info("User " + userId + " added to queue for event " + eventId);

        return QueueAccessResult.waiting(
                getPositionInQueue(userId, eventId),
                getQueueSize(eventId)
        );
    }

    /**
     * Read-only access status. Unlike requestSelectionAccess, this method does
     * not enqueue a new user. Use it for polling a waiting room page.
     */
    public synchronized QueueAccessResult getSelectionAccessStatus(UUID userId, UUID eventId) {
        ensureUserExists(userId);
        ensureEventExists(eventId);

        removeUsersThatOutOfTime(eventId);
        promoteWaitingUsers(eventId);

        LocalDateTime expiration = getAccessExpiration(userId, eventId);
        if (expiration != null) {
            return QueueAccessResult.allowed(expiration);
        }

        int position = getPositionInQueue(userId, eventId);
        if (position > 0) {
            return QueueAccessResult.waiting(position, getQueueSize(eventId));
        }

        return QueueAccessResult.waiting(-1, getQueueSize(eventId));
    }

    /**
     * Used by ticket-reservation endpoints. This method only verifies an
     * already-granted access window; it never enqueues the user. That keeps the
     * queue semantics aligned with the requirement: the queue is before ticket
     * selection, not inside the reservation call.
     */
    public synchronized void requireSelectionAccess(UUID userId, UUID eventId) {
        ensureUserExists(userId);
        ensureEventExists(eventId);
        removeUsersThatOutOfTime(eventId);

        if (!hasSelectAccess(userId, eventId)) {
            throw new IllegalStateException("Selection access expired or was not granted. Please join the queue again.");
        }
    }

    public synchronized int getPositionInQueue(UUID userId, UUID eventId) {
        ensureUserExists(userId);

        Queue<UUID> queue = queuePerEvent.get(eventId);
        if (queue == null) {
            return -1;
        }

        List<UUID> currentQueue = new ArrayList<>(queue);

        for (int i = 0; i < currentQueue.size(); i++) {
            if (currentQueue.get(i).equals(userId)) {
                return i + 1;
            }
        }
        return -1;
    }

    public synchronized List<UUID> releaseBatch(UUID eventId, int batchSize) {
        logger.info("Attempting to release a batch of " + batchSize + " users for event: " + eventId);

        ensureEventExists(eventId);
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }

        removeUsersThatOutOfTime(eventId);

        Queue<UUID> currentEventQueue = queuePerEvent.get(eventId);

        if (currentEventQueue == null || currentEventQueue.isEmpty()) {
            logger.info("No users waiting in queue for event: " + eventId);
            return new ArrayList<>();
        }

        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.computeIfAbsent(eventId, id -> new HashMap<>());

        int availableSlots = maxConcurrentSelectors - allowedForEvent.size();
        if (availableSlots <= 0) {
            return new ArrayList<>();
        }

        int actualBatchSize = Math.min(batchSize, availableSlots);
        List<UUID> releasedUsers = new ArrayList<>();

        for (int i = 0; i < actualBatchSize; i++) {
            UUID releasedUserId = currentEventQueue.poll();
            if (releasedUserId == null) {
                break;
            }

            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(howManyMinutesToStartSelection);
            allowedForEvent.put(releasedUserId, expiresAt);
            releasedUsers.add(releasedUserId);

            if (notifier != null) {
                notifier.notifyUser(
                        releasedUserId,
                        "Your turn has arrived. You can now select tickets for event " + eventId
                );
            }
        }

        logger.info("Successfully released " + releasedUsers.size() + " users from queue for event " + eventId);
        return releasedUsers;
    }

    public synchronized boolean hasSelectAccess(UUID userId, UUID eventId) {
        ensureUserExists(userId);
        ensureEventExists(eventId);

        LocalDateTime expirationTime = getAccessExpiration(userId, eventId);
        return expirationTime != null;
    }

    public synchronized LocalDateTime getAccessExpiration(UUID userId, UUID eventId) {
        Map<UUID, LocalDateTime> allowedForEvent = perEventAllowedUsersToStartSelection.get(eventId);

        if (allowedForEvent == null) {
            return null;
        }

        LocalDateTime expirationTime = allowedForEvent.get(userId);
        if (expirationTime == null) {
            return null;
        }

        if (!expirationTime.isAfter(LocalDateTime.now())) {
            allowedForEvent.remove(userId);
            return null;
        }

        return expirationTime;
    }

    public synchronized void finishAccess(UUID userId, UUID eventId) {
        logger.info("Finishing selection access for user: " + userId + " on event: " + eventId);

        ensureUserExists(userId);
        ensureEventExists(eventId);

        Map<UUID, LocalDateTime> allowedForEvent = perEventAllowedUsersToStartSelection.get(eventId);
        if (allowedForEvent != null) {
            allowedForEvent.remove(userId);
        }
    }

    public synchronized int getQueueSize(UUID eventId) {
        Queue<UUID> queue = queuePerEvent.get(eventId);
        return queue == null ? 0 : queue.size();
    }

    private void removeUsersThatOutOfTime(UUID eventId) {
        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.get(eventId);

        if (allowedForEvent == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        allowedForEvent.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    private void promoteWaitingUsers(UUID eventId) {
        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.computeIfAbsent(eventId, id -> new HashMap<>());

        int availableSlots = maxConcurrentSelectors - allowedForEvent.size();
        if (availableSlots > 0) {
            releaseBatch(eventId, availableSlots);
        }
    }

    public int getHowManyMinutesToStartSelection() {
        return howManyMinutesToStartSelection;
    }

    public void setHowManyMinutesToStartSelection(int howManyMinutesToStartSelection) {
        if (howManyMinutesToStartSelection <= 0) {
            throw new IllegalArgumentException("Minutes to start selection must be positive");
        }
        this.howManyMinutesToStartSelection = howManyMinutesToStartSelection;
    }

    public int getMaxConcurrentSelectors() {
        return this.maxConcurrentSelectors;
    }

    public void setMaxConcurrentSelectors(int maxConcurrentSelectors) {
        if (maxConcurrentSelectors <= 0) {
            throw new IllegalArgumentException("Max concurrent selectors must be positive");
        }
        this.maxConcurrentSelectors = maxConcurrentSelectors;
    }

    private void ensureUserExists(UUID userId) {
        if (userId == null) {
            throw new DomainException("userId is required");
        }
    }

    private void ensureEventExists(UUID eventId) {
        if (eventId == null) {
            throw new DomainException("eventId is required");
        }
    }

    public record QueueSnapshot(
            UUID eventId,
            int queueSize,
            int activeSelectorsCount,
            int maxConcurrentSelectors,
            int minutesToStartSelection
    ) {
    }

    public synchronized List<QueueSnapshot> getAllQueueSnapshots() {
        Set<UUID> eventIds = new HashSet<>();
        eventIds.addAll(queuePerEvent.keySet());
        eventIds.addAll(perEventAllowedUsersToStartSelection.keySet());

        List<QueueSnapshot> snapshots = new ArrayList<>();

        for (UUID eventId : eventIds) {
            removeUsersThatOutOfTime(eventId);

            Queue<UUID> queue = queuePerEvent.get(eventId);
            Map<UUID, LocalDateTime> activeSelectors = perEventAllowedUsersToStartSelection.get(eventId);

            int queueSize = queue == null ? 0 : queue.size();
            int activeSelectorsCount = activeSelectors == null ? 0 : activeSelectors.size();

            if (queueSize == 0 && activeSelectorsCount == 0) {
                continue;
            }

            snapshots.add(new QueueSnapshot(
                    eventId,
                    queueSize,
                    activeSelectorsCount,
                    maxConcurrentSelectors,
                    howManyMinutesToStartSelection
            ));
        }

        return snapshots;
    }

    public synchronized QueueSnapshot getQueueSnapshot(UUID eventId) {
        ensureEventExists(eventId);
        removeUsersThatOutOfTime(eventId);

        Queue<UUID> queue = queuePerEvent.get(eventId);
        Map<UUID, LocalDateTime> activeSelectors = perEventAllowedUsersToStartSelection.get(eventId);

        return new QueueSnapshot(
                eventId,
                queue == null ? 0 : queue.size(),
                activeSelectors == null ? 0 : activeSelectors.size(),
                maxConcurrentSelectors,
                howManyMinutesToStartSelection
        );
    }

    public synchronized void clearQueue(UUID eventId) {
        ensureEventExists(eventId);

        Queue<UUID> queue = queuePerEvent.get(eventId);
        if (queue != null) {
            queue.clear();
        }

        Map<UUID, LocalDateTime> activeSelectors = perEventAllowedUsersToStartSelection.get(eventId);
        if (activeSelectors != null) {
            activeSelectors.clear();
        }
    }
}
