package org.example.ApplicationLayer;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class QueueManagerTest {


    @Test
    //משתמש נכנס לתור כשיש עומס
    public void requestSelectionAccess_whenFull_ReturnsWaiting()
    {
        QueueManager queueManager = new QueueManager();
        queueManager.setMaxConcurrentSelectors(1);

        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID eventID = UUID.randomUUID();

        //המשתמש אמור ישר להיכנס כי אין אפילו תור
        queueManager.requestSelectionAccess(user1, eventID);

        //המשתמש הראשון בנתיים בוחר ואנחנו מנסים להכניס את המשתמש השני והוא נכנס לתור
        QueueAccessResult result = queueManager.requestSelectionAccess(user2, eventID);

        //בנתיים המשתמש השני צריך לחכות
        assertFalse(result.isAllowed());
        assertEquals(1, result.getUserPositionInQueue());
        assertEquals(1, result.getQueueSize());

    }

    @Test
    //משתמש לא צריך להיכנס לתור כשיש מקום
    public void requestSelectionAccess_whenEmpty_ReturnsAllowed()
    {
        QueueManager queueManager = new QueueManager();
        queueManager.setMaxConcurrentSelectors(1);

        UUID user1 = UUID.randomUUID();
        UUID eventID = UUID.randomUUID();

        //המשתמש אמור ישר להיכנס כי אין אפילו תור
        QueueAccessResult result = queueManager.requestSelectionAccess(user1, eventID);

        //המשתמש הראשון בנתיים בוחר ואנחנו מנסים להכניס את המשתמש השני והוא נכנס לתור
        //בנתיים המשתמש השני צריך לחכות
        assertTrue(result.isAllowed());
        assertTrue(queueManager.hasSelectAccess(user1, eventID));
    }

    @Test
    public void hasAccess_WhenExpired_ReturnsFalse() throws InterruptedException
    {
        QueueManager queueManager = new QueueManager();
        queueManager.setHowManyMinutesToStartSelection(0);

        UUID user1 = UUID.randomUUID();
        UUID eventID = UUID.randomUUID();

        queueManager.requestSelectionAccess(user1, eventID);

        Thread.sleep(1);

        assertFalse(queueManager.hasSelectAccess(user1, eventID));
    }

    @Test
    //משתמש שהיה בתור מקודם לבחירת כרטיסים
    public void releaseBatch_WhenSlotAvailable_ReleasesNextUser()
    {
        QueueManager queueManager = new QueueManager();
        queueManager.setMaxConcurrentSelectors(1);

        UUID eventID = UUID.randomUUID();
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();


        //משתמש 1 נכנס ישר
        queueManager.requestSelectionAccess(user1, eventID);

        //משתמש 2 נכנס לתור
        QueueAccessResult user2Result = queueManager.requestSelectionAccess(user2, eventID);

        //המשתמש הראשון עוד בוחר
        assertFalse(user2Result.isAllowed());

        //המשתמש השני חייב להיות במקום הראשון בתור
        assertEquals(1, user2Result.getUserPositionInQueue());

        //המשתמש הראשון מסיים את הבחירה
        queueManager.finishAccess(user1, eventID);

        //מקדמים את הקבוצה, במקרה הזה זה רק המשתמש השני
        List<UUID> releasedUsers = queueManager.releaseBatch(eventID, 1);

        //אז הוא קודם לבחירה
        assertEquals(1, releasedUsers.size());

        assertTrue(queueManager.hasSelectAccess(user2, eventID));
        assertEquals(-1, queueManager.getPositionInQueue(user2, eventID));
        assertEquals(0, queueManager.getQueueSize(eventID));
    }


}