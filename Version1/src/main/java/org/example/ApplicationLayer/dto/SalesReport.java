package org.example.ApplicationLayer.dto;

import java.util.List;
import java.util.UUID;

public class SalesReport {
    List<UUID> eventIds;
    List<UUID> ticketIds;
    double totalRevenue;

    public SalesReport(List<UUID> eventIds, List<UUID> ticketIds, double totalRevenue) {
        this.eventIds = eventIds;
        this.ticketIds = ticketIds;
        this.totalRevenue = totalRevenue;
    }

    public String toString() {
        return "SalesReport{" +
                "eventIds=" + eventIds +
                ", ticketIds=" + ticketIds +
                ", totalRevenue=" + totalRevenue +
                '}';
    }
}
