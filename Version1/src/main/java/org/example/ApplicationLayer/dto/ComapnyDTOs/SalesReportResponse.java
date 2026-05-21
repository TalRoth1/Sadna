package org.example.ApplicationLayer.dto.ComapnyDTOs;

import java.util.UUID;

/**
 * Payload for the sales report endpoint.
 */
public class SalesReportResponse {
    public final UUID companyId;
    public final String ownerUsername;
    public final String report;

    public SalesReportResponse(UUID companyId, String ownerUsername, String report) {
        this.companyId = companyId;
        this.ownerUsername = ownerUsername;
        this.report = report;
    }
}
