package org.example.ApplicationLayer.dto.AdminDTOs;

import java.time.LocalDateTime;

public class AdminAnalyticsDTO {
    public int registeredUsersCount;
    public int loggedInUsersCount;
    public int activeCompaniesCount;
    public int activeQueuesCount;
    public int activePurchasesCount;
    public int totalPurchasesCount;
    public LocalDateTime createdAt;
}