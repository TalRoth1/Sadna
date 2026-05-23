package org.example.ApplicationLayer;

import org.example.DomainLayer.DomainException;
import org.example.DomainLayer.NotificationAggregate.INotifier;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;



public class QueueManager
{
    private Map<UUID, Queue<UUID>> queuePerEvent = new LinkedHashMap<>(); //eventId and userid
    private Map<UUID, Map<UUID, LocalDateTime>> perEventAllowedUsersToStartSelection = new LinkedHashMap<>();

    private int maxConcurrentSelectors = 10;

    private int howManyMinutesToStartSelection = 10;

    private static final Logger logger = Logger.getLogger(EventService.class.getName());

    private INotifier notifier;

    public QueueManager() {

    }


    public QueueManager(INotifier notifier) {
        this.notifier = notifier;
    }
    public QueueManager queueManager(INotifier notifier) {
        return new QueueManager(notifier);
    }

    public synchronized QueueAccessResult requestSelectionAccess(UUID userId, UUID eventId)
    {
        logger.info("Requesting selection access: userId=" + userId + ", eventId=" + eventId);

        ensureUserExists(userId);
        ensureEventExists(eventId);

        removeUsersThatOutOfTime(eventId);

        promoteWaitingUsers(eventId);

        if (hasSelectAccess(userId, eventId)) {
            logger.info("Access already granted for user " + userId + " on event " + eventId);
            return QueueAccessResult.allowed();
        }

        Queue<UUID> currentEventQueue = queuePerEvent.computeIfAbsent(eventId, k -> new LinkedList<>());

        if (currentEventQueue.contains(userId)) {
            logger.info("User " + userId + " is already in queue for event ");
            return QueueAccessResult.waiting(
                    getPositionInQueue(userId, eventId),
                    getQueueSize(eventId)
            );
        }

        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.computeIfAbsent(eventId, id -> new HashMap<>());



        if (allowedForEvent.size() < maxConcurrentSelectors && currentEventQueue.isEmpty()) {
            allowedForEvent.put(userId, LocalDateTime.now().plusMinutes(howManyMinutesToStartSelection));
            logger.info("Immediate access granted to user " + userId + " for event " + eventId + " (Capacity: " + allowedForEvent.size() + "/" + maxConcurrentSelectors + ")");
            return QueueAccessResult.allowed();
        }


        currentEventQueue.add(userId);

        logger.info("User " + userId + " added to queue for event " + eventId);

        return QueueAccessResult.waiting(
                getPositionInQueue(userId, eventId),
                getQueueSize(eventId)
        );
    }


    public synchronized int getPositionInQueue(UUID userId, UUID eventId)
    {
        logger.info("Checking queue position for user: " + userId + " on event: " + eventId);
        ensureUserExists(userId);

        Queue<UUID> queue = queuePerEvent.get(eventId);
        if (queue == null) {
            return -1;
        }

        List<UUID> currentQueue = new ArrayList<>(queue);

        for (int i = 0; i < currentQueue.size(); i++) {
            if (currentQueue.get(i).equals(userId)) {
                logger.info("User " + userId + " found in queue at position: " + i + 1);
                return i + 1;
            }
        }
        logger.warning("User " + userId + " not found in queue for event " + eventId);
        return -1;
    }

    public synchronized List<UUID> releaseBatch(UUID eventId, int batchSize)
    {
        logger.info("Attempting to release a batch of " + batchSize + " users for event: " + eventId);

        ensureEventExists(eventId);
        removeUsersThatOutOfTime(eventId);

        Queue<UUID> currentEventQueue = queuePerEvent.get(eventId);

        if (currentEventQueue == null || currentEventQueue.isEmpty()) {
            logger.info("No users waiting in queue for event: " + eventId);
            return new ArrayList<>();
        }

        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.computeIfAbsent(eventId, id -> new HashMap<>());

        if (maxConcurrentSelectors - allowedForEvent.size() <= 0) {
            return new ArrayList<>();
        }

        int actualBatchSize = Math.min(batchSize, maxConcurrentSelectors - allowedForEvent.size());

        List<UUID> releasedUsers = new ArrayList<>();

        for (int i = 0; i < actualBatchSize; i++) {

            UUID releasedUserID = currentEventQueue.poll();

            allowedForEvent.put(
                    releasedUserID,
                    LocalDateTime.now().plusMinutes(howManyMinutesToStartSelection)
            );

            releasedUsers.add(releasedUserID);

            if (notifier != null) {
                notifier.notifyUser(
                        releasedUserID,
                        "Your turn has arrived. You can now select tickets for event " + eventId
                );
            }
        }
        logger.info("Successfully released " + releasedUsers.size() + " users from queue for event " + eventId);
        return releasedUsers;
    }

    public synchronized boolean hasSelectAccess(UUID userId, UUID eventId) {

        logger.info("Checking if user " + userId + " has select access for event " + eventId);
        ensureUserExists(userId);

        Map<UUID, LocalDateTime> allowedForEvent = perEventAllowedUsersToStartSelection.get(eventId);

        if (allowedForEvent == null) {
            logger.info("No active selectors list found for event " + eventId);
            return false;
        }

        LocalDateTime expirationTime = allowedForEvent.get(userId);

        if (expirationTime == null) {
            logger.info("User " + userId + " does not have an active selection slot for event " + eventId);
            return false;
        }

        if (expirationTime.isBefore(LocalDateTime.now())) {
            logger.warning("Access expired for user " + userId + " on event " + eventId + ". Expiry was at: " + expirationTime);
            allowedForEvent.remove(userId);
            return false;
        }

        logger.info("User " + userId + " has valid access until: " + expirationTime);
        return true;
    }

    public synchronized void finishAccess(UUID userId, UUID eventId) {

        logger.info("Finishing selection access for user: " + userId + " on event: " + eventId);

        ensureUserExists(userId);

        Map<UUID, LocalDateTime> allowedForEvent = perEventAllowedUsersToStartSelection.get(eventId);
        if (allowedForEvent != null) {
            allowedForEvent.remove(userId);
        }
    }

    public synchronized int getQueueSize(UUID eventId) {

        logger.info("Checking queue size for event: " + eventId);

        Queue<UUID> queue = queuePerEvent.get(eventId);

        if (queue == null) {
            return -1;
        }

        return queue.size();
    }


    private void removeUsersThatOutOfTime(UUID eventId) {
        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.get(eventId);

        if (allowedForEvent == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        allowedForEvent.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    private void promoteWaitingUsers(UUID eventId)
    {
        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.computeIfAbsent(eventId, id -> new HashMap<>());

        if (maxConcurrentSelectors - allowedForEvent.size() > 0) {
            releaseBatch(eventId, maxConcurrentSelectors - allowedForEvent.size());
        }
    }


    public void setHowManyMinutesToStartSelection(int howManyMinutesToStartSelection) {
        this.howManyMinutesToStartSelection = howManyMinutesToStartSelection;
    }

    public int getMaxConcurrentSelectors() {
        return this.maxConcurrentSelectors;
    }

    public void setMaxConcurrentSelectors(int maxConcurrentSelectors) {
        this.maxConcurrentSelectors = maxConcurrentSelectors;
    }

    private void ensureUserExists(UUID userId)
    {
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
            Queue<UUID> queue = queuePerEvent.get(eventId);
            Map<UUID, LocalDateTime> activeSelectors = perEventAllowedUsersToStartSelection.get(eventId);

            snapshots.add(new QueueSnapshot(
                    eventId,
                    queue == null ? 0 : queue.size(),
                    activeSelectors == null ? 0 : activeSelectors.size(),
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