package org.example.ApplicationLayer;

public class QueueAccessResult
{
    private final boolean allowed;
    private final int position;
    private final int queueSize;

    public QueueAccessResult(boolean allowed, int position, int queueSize)
    {
        this.allowed = allowed;
        this.position = position;
        this.queueSize = queueSize;
    }

    public static QueueAccessResult allowed()
    {
        return new QueueAccessResult(true, 0, 0);
    }
    public static QueueAccessResult waiting(int position, int queueSize)
    {
        return new QueueAccessResult(false, position, queueSize);
    }
    public boolean isAllowed()
    {
        return allowed;
    }
    public int getPosition()
    {
        return position;
    }
    public int getQueueSize()
    {
        return queueSize;
    }
}
