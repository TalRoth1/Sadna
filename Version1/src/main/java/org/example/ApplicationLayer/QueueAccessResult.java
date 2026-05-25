package org.example.ApplicationLayer;

import java.time.LocalDateTime;

public class QueueAccessResult {
    private final boolean allowed;
    private final int userPositionInQueue;
    private final int queueSize;
    private final LocalDateTime accessExpiresAt;

    public QueueAccessResult(
            boolean allowed,
            int userPositionInQueue,
            int queueSize,
            LocalDateTime accessExpiresAt
    ) {
        this.allowed = allowed;
        this.userPositionInQueue = userPositionInQueue;
        this.queueSize = queueSize;
        this.accessExpiresAt = accessExpiresAt;
    }

    public static QueueAccessResult allowed(LocalDateTime accessExpiresAt) {
        return new QueueAccessResult(true, 0, 0, accessExpiresAt);
    }

    public static QueueAccessResult waiting(int userPositionInQueue, int queueSize) {
        return new QueueAccessResult(false, userPositionInQueue, queueSize, null);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public int getUserPositionInQueue() {
        return userPositionInQueue;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public LocalDateTime getAccessExpiresAt() {
        return accessExpiresAt;
    }
}
