package org.example.ApplicationLayer.CompanyDTOs;

import java.util.UUID;

/**
 * Payload for the hierarchy endpoint.
 * Mermaid chart wrapped with companyId for context.
 */
public class HierarchyResponse {
    public final UUID companyId;
    public final String mermaidChart;

    public HierarchyResponse(UUID companyId, String mermaidChart) {
        this.companyId = companyId;
        this.mermaidChart = mermaidChart;
    }
}
