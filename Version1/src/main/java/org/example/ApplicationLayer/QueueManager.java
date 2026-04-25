package org.example.ApplicationLayer;

import org.example.DomainLayer.DomainException;

import java.time.LocalDateTime;
import java.util.*;

public class QueueManager
{
    private Map<Integer, Queue<String>> queuePerEvent = new LinkedHashMap<>(); //eventId and userid
    private Map<Integer, Map<String, LocalDateTime>> perEventAllowedUsersToStartSelection = new LinkedHashMap<>();

    //מהרגע שהיוזר יצא מהתור, כמה זמן יש לו להתחיל את הבחירה?
    private int howManyMinutesToStartSelection = 10;

    public synchronized void joinQueue(String userId, int eventId) {
        validateUser(userId);

        //אם למשתמש כבר יש גישה לתהליך הבחירה אין למה להכניס אותו לתור שוב
        if (hasAccess(userId, eventId)) {
            return;
        }

        //אם יש תור לאירוע נשתמש בו, אחרת ניצור תור חדש
        Queue<String> queue = queuePerEvent.get(eventId);

        if (queue == null)
        {
            queue = new LinkedList<>();
            queuePerEvent.put(eventId, queue);
        }

        //אם המשתמש כבר בתור אז אין טעם להכניס אותו שוב
        if (queue.contains(userId)) {
            return; // כבר בתור
        }


        //נכניס את ה-user לתור
        queue.add(userId);
    }

    public synchronized int getPosition(String userId, int eventId) {
        validateUser(userId);

        //אם אין תור בפרט נחזיר -1 כי אין דרך לחשב מיקום
        Queue<String> queue = queuePerEvent.get(eventId);
        if (queue == null) {
            return -1;
        }

        List<String> currentQueue = new ArrayList<>(queue);

        for (int i = 0; i < currentQueue.size(); i++) {
            if (currentQueue.get(i).equals(userId)) {
                return i + 1;
            }
        }

        return -1;
    }

    public synchronized List<String> releaseBatch(int eventId, int batchSize) {
        if (batchSize <= 0) {
            throw new DomainException("קבוצה לשחרור חייבת להיות בגודל חיובי");
        }

        //אם התור ריק או לא קיים נחזיר רשימה ריקה
        Queue<String> queue = queuePerEvent.get(eventId);
        if (queue == null || queue.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.computeIfAbsent(eventId, id -> new HashMap<>());

        List<String> releasedUsers = new ArrayList<>();

        for (int i = 0; i < batchSize && !queue.isEmpty(); i++) {
            String userId = queue.poll();
            allowedForEvent.put(userId, LocalDateTime.now().plusMinutes(howManyMinutesToStartSelection));
            releasedUsers.add(userId);
        }

        return releasedUsers;
    }

    //האם המשתמש כבר יצא מהתור והוא יכול להתחיל לבחור כרטיסים
    public synchronized boolean hasAccess(String userId, int eventId) {
        validateUser(userId);

        //האם יש רשימת מורשים להתחיל את הבחירה, כלומר סיימו את התור וממתינים שיתחילו
        Map<String, LocalDateTime> allowedForEvent = perEventAllowedUsersToStartSelection.get(eventId);
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
    public synchronized void consumeAccess(String userId, int eventId) {
        validateUser(userId);

        Map<String, LocalDateTime> allowedForEvent = perEventAllowedUsersToStartSelection.get(eventId);
        if (allowedForEvent != null) {
            allowedForEvent.remove(userId);
        }
    }

    public synchronized int getQueueSize(int eventId) {
        Queue<String> queue = queuePerEvent.get(eventId);
        return queue == null ? 0 : queue.size();
    }

    public synchronized void clearQueue(int eventId) {
        queuePerEvent.remove(eventId);
        perEventAllowedUsersToStartSelection.remove(eventId);
    }

    private void validateUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new DomainException("userId is required");
        }
    }
}