package org.example.ApplicationLayer.EventDTOs;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Flat / nullable version of the Domain's EventSearchCriteria record.
 * The Service is responsible for converting null fields to Optional.empty().
 *
 * All fields are optional — an empty request matches every publicly-visible event.
 */
public class EventSearchCriteriaRequest {
    public String text;
    public String location;
    public Double priceMin;
    public Double priceMax;
    public LocalDateTime dateFrom;
    public LocalDateTime dateTo;
    public Double minEventRating;
    public Double minCompanyRating;
    public UUID companyId;

    public EventSearchCriteriaRequest() {}
}
