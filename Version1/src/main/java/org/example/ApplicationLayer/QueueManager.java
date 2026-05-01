package org.example.ApplicationLayer;

import org.example.DomainLayer.DomainException;

import java.time.LocalDateTime;
import java.util.*;

public class QueueManager
{
    private Map<UUID, Queue<UUID>> queuePerEvent = new LinkedHashMap<>(); //eventId and userid
    private Map<UUID, Map<UUID, LocalDateTime>> perEventAllowedUsersToStartSelection = new LinkedHashMap<>();

    private int maxConcurrentSelectors = 10; //הגדרת העומס על המערכת


    //מהרגע שהיוזר יצא מהתור, כמה זמן יש לו להתחיל את הבחירה?
    private int howManyMinutesToStartSelection = 10;

    public synchronized QueueAccessResult requestSelectionAccess(UUID userId, UUID eventId) {
        ensureUserExists(userId);
        ensureEventExists(eventId);

        cleanupExpiredAccess(eventId);
        releaseAvailableSlots(eventId);

        if (hasAccess(userId, eventId)) {
            return QueueAccessResult.allowed();
        }

        Queue<UUID> queue = queuePerEvent.computeIfAbsent(eventId, k -> new LinkedList<>());

        if (queue.contains(userId)) {
            return QueueAccessResult.waiting(
                    getPosition(userId, eventId),
                    getQueueSize(eventId)
            );
        }

        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.computeIfAbsent(eventId, id -> new HashMap<>());

        boolean hasFreeSlot = allowedForEvent.size() < maxConcurrentSelectors;
        boolean noOneWaitingBeforeHim = queue.isEmpty();

        if (hasFreeSlot && noOneWaitingBeforeHim) {
            allowedForEvent.put(userId, LocalDateTime.now().plusMinutes(howManyMinutesToStartSelection));
            return QueueAccessResult.allowed();
        }

        queue.add(userId);

        return QueueAccessResult.waiting(
                getPosition(userId, eventId),
                getQueueSize(eventId)
        );
    }


    public synchronized void joinQueue(UUID userId, UUID eventId) {
        ensureUserExists(userId);

        //אם למשתמש כבר יש גישה לתהליך הבחירה אין למה להכניס אותו לתור שוב
        if (hasAccess(userId, eventId)) {
            return;
        }

        //אם יש תור לאירוע נשתמש בו, אחרת ניצור תור חדש
        Queue<UUID> queue = queuePerEvent.computeIfAbsent(eventId, k -> new LinkedList<>());

        //אם המשתמש כבר בתור אז אין טעם להכניס אותו שוב
        if (!queue.contains(userId)) {
            queue.add(userId);
        }
    }

    public synchronized int getPosition(UUID userId, UUID eventId) {
        ensureUserExists(userId);

        //אם אין תור בפרט נחזיר -1 כי אין דרך לחשב מיקום
        Queue<UUID> queue = queuePerEvent.get(eventId);
        if (queue == null) {
            return -1;
        }

        //אחרת נחשב את המיקום שלו בתור
        List<UUID> currentQueue = new ArrayList<>(queue);

        for (int i = 0; i < currentQueue.size(); i++) {
            if (currentQueue.get(i).equals(userId)) {
                return i + 1;
            }
        }

        return -1;
    }

    public synchronized List<UUID> releaseBatch(UUID eventId, int batchSize) {
        if (batchSize <= 0) {
            throw new DomainException("קבוצה לשחרור חייבת להיות בגודל חיובי");
        }

        ensureEventExists(eventId);
        cleanupExpiredAccess(eventId);

        Queue<UUID> queue = queuePerEvent.get(eventId);
        if (queue == null || queue.isEmpty()) {
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

        for (int i = 0; i < actualBatchSize && !queue.isEmpty(); i++) {
            UUID releasedUserID = queue.poll();
            allowedForEvent.put(
                    releasedUserID,
                    LocalDateTime.now().plusMinutes(howManyMinutesToStartSelection)
            );
            releasedUsers.add(releasedUserID);
        }

        return releasedUsers;
    }

    //האם המשתמש כבר יצא מהתור והוא יכול להתחיל לבחור כרטיסים
    public synchronized boolean hasAccess(UUID userId, UUID eventId) {
        ensureUserExists(userId);

        //האם יש רשימת מורשים להתחיל את הבחירה, כלומר סיימו את התור וממתינים שיתחילו
        Map<UUID, LocalDateTime> allowedForEvent = perEventAllowedUsersToStartSelection.get(eventId);
        if (allowedForEvent == null) {
            return false;
        }

        //האם המשתמש ברשימה הזאת?
        LocalDateTime expirationTime = allowedForEvent.get(userId);
        if (expirationTime == null) {
            return false;
        }

        //האם הזמן שלו עדיין בתוקף?
        if (expirationTime.isBefore(LocalDateTime.now())) {
            allowedForEvent.remove(userId);
            return false;
        }

        return true;
    }

    //המשתמש השתמש בהרשאה שלו, ניתן למחוק אותו מהתור של המורשים
    public synchronized void consumeAccess(UUID userId, UUID eventId) {
        ensureUserExists(userId);

        Map<UUID, LocalDateTime> allowedForEvent = perEventAllowedUsersToStartSelection.get(eventId);
        if (allowedForEvent != null) {
            allowedForEvent.remove(userId);
        }
    }

    public synchronized int getQueueSize(UUID eventId) {
        Queue<UUID> queue = queuePerEvent.get(eventId);

        if (queue == null) {
            return -1;
        }

        return queue.size();
    }

    public synchronized void clearQueue(UUID eventId) {
        queuePerEvent.remove(eventId);
        perEventAllowedUsersToStartSelection.remove(eventId);
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

    private void cleanupExpiredAccess(UUID eventId) {
        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.get(eventId);

        if (allowedForEvent == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        allowedForEvent.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    private void releaseAvailableSlots(UUID eventId)
    {
        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.computeIfAbsent(eventId, id -> new HashMap<>());

        int availableSlots = maxConcurrentSelectors - allowedForEvent.size();

        if (availableSlots > 0) {
            releaseBatch(eventId, availableSlots);
        }
    }


    public void setHowManyMinutesToStartSelection(int howManyMinutesToStartSelection) {
        this.howManyMinutesToStartSelection = howManyMinutesToStartSelection;
    }

    public int getMaxConcurrentSelectors() {
        return maxConcurrentSelectors;
    }

    public void setMaxConcurrentSelectors(int maxConcurrentSelectors) {
        this.maxConcurrentSelectors = maxConcurrentSelectors;
    }
}