package org.example.ApplicationLayer.dto.AdminDTOs;

import java.util.UUID;

public class AdminQueueSnapshotDTO {
    public UUID eventId;
    public int queueSize;
    public int activeSelectorsCount;
    public int maxConcurrentSelectors;
    public int minutesToStartSelection;
}