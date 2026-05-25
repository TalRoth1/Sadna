package org.example.ApplicationLayer.dto.CompanyDTOs;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for events returned to owners listing subordinate-managed events.
 */
public record SubordinateEventDto(
        UUID eventId,
        UUID companyId,
        String companyName,
        double companyRating,
        String name,
        String artist,
        String eventType,
        LocalDateTime date,
        String location,
        double rating,
        double priceMin,
        double priceMax,
        int availableTickets,
        int totalTickets,
        String managerEmail
) {
}
