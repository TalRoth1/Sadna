package org.example.ApplicationLayer.dto.ComapnyDTOs;

import java.util.List;
import java.util.UUID;

/**
 * Payload for createCompany.
 * Contains the essential company info the client needs right after creation.
 */
public class CompanyResponse {
    public final UUID id;
    public final String name;
    public final String founderUsername;
    public final double rating;
    public final boolean isActive;
    public final List<UUID> eventIds;

    public CompanyResponse(UUID id, String name, String founderUsername,
                           double rating, boolean isActive, List<UUID> eventIds) {
        this.id = id;
        this.name = name;
        this.founderUsername = founderUsername;
        this.rating = rating;
        this.isActive = isActive;
        this.eventIds = eventIds;
    }
}
