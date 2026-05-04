package org.example.ApplicationLayer;

import org.example.DomainLayer.DomainException;

import java.time.LocalDateTime;
import java.util.*;

public class QueueManager
{
    private Map<UUID, Queue<UUID>> queuePerEvent = new LinkedHashMap<>(); //eventId and userid
    private Map<UUID, Map<UUID, LocalDateTime>> perEventAllowedUsersToStartSelection = new LinkedHashMap<>();
    //זאת בעצם רשימה של רשימות, לפי אירוע - רשימה של כל ה-users שיכולים לבחור ועד מתי

    private int maxConcurrentSelectors = 10; //הגדרת העומס על המערכת

    //מהרגע שהיוזר יצא מהתור, כמה זמן יש לו להתחיל את הבחירה?
    private int howManyMinutesToStartSelection = 10;

    public synchronized QueueAccessResult requestSelectionAccess(UUID userId, UUID eventId)
    {
        ensureUserExists(userId);
        ensureEventExists(eventId);

        //מי שכבר פג תוקף הזמן לגישה שלהם נמחק מהתור
        removeUsersThatOutOfTime(eventId);

        //מי שמחכה לפני המשתמש שנכנס בדיוק ננסה לקדם אותו
        promoteWaitingUsers(eventId);

        //אם ל-user שאנחנו ביקשנו עבורו גישה כבר יש גישה אז נחזיר allowed
        if (hasSelectAccess(userId, eventId)) {
            return QueueAccessResult.allowed();
        }

        Queue<UUID> currentEventQueue = queuePerEvent.computeIfAbsent(eventId, k -> new LinkedList<>());

        //אם הוא כבר בתור אז נחזיר לו את המיקום הנוכחי שלו בתור ואת העובדה שהוא מחכה
        if (currentEventQueue.contains(userId)) {
            return QueueAccessResult.waiting(
                    getPositionInQueue(userId, eventId),
                    getQueueSize(eventId)
            );
        }

        //רשימת כל אלו שסיימו לחכות בתור ויכולים להתחיל לבחור
        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.computeIfAbsent(eventId, id -> new HashMap<>());


        /*
        אם מספר האנשים שבוחרים כרגע קטן מהמקסימום ואף אחד מחכה לפני המשתמש הנוכחי
        אז ניתן למשתמש הרשאת כניסה ונכניס אותו לרשימת אלה שיכולים לבחור
         */
        if (allowedForEvent.size() < maxConcurrentSelectors && currentEventQueue.isEmpty()) {
            allowedForEvent.put(userId, LocalDateTime.now().plusMinutes(howManyMinutesToStartSelection));
            return QueueAccessResult.allowed();
        }


        //אם המשתמש לא יכול לקבל גישה ממש עכשיו, אז נכניס אותו לתור של האירוע ונחזיר שהוא מחכה
        currentEventQueue.add(userId);

        return QueueAccessResult.waiting(
                getPositionInQueue(userId, eventId),
                getQueueSize(eventId)
        );
    }


    public synchronized int getPositionInQueue(UUID userId, UUID eventId)
    {
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

    //הרעיון במתודה הזאת היא לתת לכמות מסוימת של ה-users הכי מקדימה בתור הרשאה להתחיל לבחור
    public synchronized List<UUID> releaseBatch(UUID eventId, int batchSize)
    {
        ensureEventExists(eventId);
        removeUsersThatOutOfTime(eventId);

        Queue<UUID> currentEventQueue = queuePerEvent.get(eventId);

        //אם אין תור אז אין את מי לשחרר
        if (currentEventQueue == null || currentEventQueue.isEmpty()) {
            return new ArrayList<>();
        }

        //רשימת המשתמשים שיכולים להתחיל לבחור
        Map<UUID, LocalDateTime> allowedForEvent =
                perEventAllowedUsersToStartSelection.computeIfAbsent(eventId, id -> new HashMap<>());

        //אם יש יותר מידי משתמשים בתהליך הבחירה ממה שאפשר אז לא נוכל לשחרר עוד
        if (maxConcurrentSelectors - allowedForEvent.size() <= 0) {
            return new ArrayList<>();
        }

        //כמה מותר לשחרר בתכלס לפי מספר האנשים בתור והדרישה?
        int actualBatchSize = Math.min(batchSize, maxConcurrentSelectors - allowedForEvent.size());

        List<UUID> releasedUsers = new ArrayList<>();

        //נוציא את ה-users מהתור ונעביר אותם לרשימת הבוחרים
        for (int i = 0; i < actualBatchSize; i++) {

            //נשחרר כל משתמש
            UUID releasedUserID = currentEventQueue.poll();
            allowedForEvent.put(
                    releasedUserID,
                    LocalDateTime.now().plusMinutes(howManyMinutesToStartSelection)
            );
            releasedUsers.add(releasedUserID);
        }

        return releasedUsers;
    }

    //האם המשתמש כבר יצא מהתור והוא יכול להתחיל לבחור כרטיסים
    public synchronized boolean hasSelectAccess(UUID userId, UUID eventId) {
        ensureUserExists(userId);

        //האם יש רשימת מורשים להתחיל את הבחירה, כלומר סיימו את התור וממתינים שיתחילו
        Map<UUID, LocalDateTime> allowedForEvent = perEventAllowedUsersToStartSelection.get(eventId);

        //מן הסתם אם אין רשימה של selectors אז הוא לא שם
        if (allowedForEvent == null) {
            return false;
        }

        //האם המשתמש ברשימה הזאת?
        LocalDateTime expirationTime = allowedForEvent.get(userId);

        //אם המשתמש עדיין בתור או בכלל לא הגיע לתור אז מן הסתם לא יהיה לו זמן סיום ליכולת הבחירה שלו
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
    public synchronized void finishAccess(UUID userId, UUID eventId) {
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

        //מי שאפשר לקדם נקדם
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
}