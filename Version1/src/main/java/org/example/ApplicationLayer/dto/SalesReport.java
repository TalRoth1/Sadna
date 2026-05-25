package org.example.ApplicationLayer.dto;

import java.util.List;
import java.util.UUID;

public class SalesReport {
    private final List<UUID> eventIds;
    private final List<UUID> ticketIds;
    private final double totalRevenue;

    public SalesReport(List<UUID> eventIds, List<UUID> ticketIds, double totalRevenue) {
        this.eventIds = eventIds;
        this.ticketIds = ticketIds;
        this.totalRevenue = totalRevenue;
    }

    public List<UUID> getEventIds() {
        return eventIds;
    }

    public List<UUID> getTicketIds() {
        return ticketIds;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public String toString() {
        return "SalesReport{" +
                "eventIds=" + eventIds +
                ", ticketIds=" + ticketIds +
                ", totalRevenue=" + totalRevenue +
                '}';
    }
}
