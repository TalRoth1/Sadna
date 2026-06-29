package org.example.ApplicationLayer.dto.CompanyDTOs;

import org.example.ApplicationLayer.dto.PurchaseDTOs.PurchaseHistoryDTO;

import java.util.UUID;
import java.util.List;

/**
 * Payload for the sales report endpoint.
 */
public class SalesReportResponse {
    public final UUID companyId;
    public final String ownerEmail;
    public final List<UUID> eventIds;
    public final List<UUID> ticketIds;
    public final double totalRevenue;
    public List<PurchaseHistoryDTO> purchases;

    public SalesReportResponse(UUID companyId, String ownerEmail, List<UUID> eventIds, List<UUID> ticketIds, double totalRevenue) {
        this.companyId = companyId;
        this.ownerEmail = ownerEmail;
        this.eventIds = eventIds;
        this.ticketIds = ticketIds;
        this.totalRevenue = totalRevenue;
        this.purchases = List.of();
    }

    public SalesReportResponse(
            UUID companyId,
            String ownerEmail,
            List<UUID> eventIds,
            List<UUID> ticketIds,
            double totalRevenue,
            List<PurchaseHistoryDTO> purchases) {
        this.companyId = companyId;
        this.ownerEmail = ownerEmail;
        this.eventIds = eventIds;
        this.ticketIds = ticketIds;
        this.totalRevenue = totalRevenue;
        this.purchases = purchases == null ? List.of() : purchases;
    }
}
