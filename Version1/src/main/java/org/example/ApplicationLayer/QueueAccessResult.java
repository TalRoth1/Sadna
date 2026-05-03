package org.example.ApplicationLayer;

public class QueueAccessResult
{
    private boolean allowed;
    private int userPositionInQueue;
    private int queueSize;

    public QueueAccessResult(boolean allowed, int userPositionInQueue, int queueSize)
    {
        this.allowed = allowed;
        this.userPositionInQueue = userPositionInQueue;
        this.queueSize = queueSize;
    }

    public static QueueAccessResult allowed()
    {
        return new QueueAccessResult(true, 0, 0);
    }
    public static QueueAccessResult waiting(int userPositionInQueue, int queueSize)
    {
        return new QueueAccessResult(false, userPositionInQueue, queueSize);
    }
    public boolean isAllowed()
    {
        return this.allowed;
    }
    public int getUserPositionInQueue()
    {
        return this.userPositionInQueue;
    }
    public int getQueueSize()
    {
        return this.queueSize;
    }
}
