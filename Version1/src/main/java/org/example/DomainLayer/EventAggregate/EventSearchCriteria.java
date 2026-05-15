package org.example.DomainLayer.EventAggregate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter parameters for guest event search (UC 2.3.1 / UC 2.3.2).
 * All fields are optional. An "empty" instance matches every publicly-visible event.
 */
public record EventSearchCriteria(
        Optional<String> text,
        Optional<String> location,
        Optional<Double> priceMin,
        Optional<Double> priceMax,
        Optional<LocalDateTime> dateFrom,
        Optional<LocalDateTime> dateTo,
        Optional<Double> minEventRating,
        Optional<Double> minCompanyRating,
        Optional<UUID> companyId) {

    public static EventSearchCriteria empty() {
        return new EventSearchCriteria(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    public EventSearchCriteria withText(String v) {
        return new EventSearchCriteria(Optional.ofNullable(v), location, priceMin, priceMax,
                dateFrom, dateTo, minEventRating, minCompanyRating, companyId);
    }

    public EventSearchCriteria withLocation(String v) {
        return new EventSearchCriteria(text, Optional.ofNullable(v), priceMin, priceMax,
                dateFrom, dateTo, minEventRating, minCompanyRating, companyId);
    }

    public EventSearchCriteria withPriceRange(Double min, Double max) {
        return new EventSearchCriteria(text, location, Optional.ofNullable(min), Optional.ofNullable(max),
                dateFrom, dateTo, minEventRating, minCompanyRating, companyId);
    }

    public EventSearchCriteria withDateRange(LocalDateTime from, LocalDateTime to) {
        return new EventSearchCriteria(text, location, priceMin, priceMax,
                Optional.ofNullable(from), Optional.ofNullable(to),
                minEventRating, minCompanyRating, companyId);
    }

    public EventSearchCriteria withMinEventRating(Double v) {
        return new EventSearchCriteria(text, location, priceMin, priceMax,
                dateFrom, dateTo, Optional.ofNullable(v), minCompanyRating, companyId);
    }

    public EventSearchCriteria withMinCompanyRating(Double v) {
        return new EventSearchCriteria(text, location, priceMin, priceMax,
                dateFrom, dateTo, minEventRating, Optional.ofNullable(v), companyId);
    }

    public EventSearchCriteria withCompanyId(UUID v) {
        return new EventSearchCriteria(text, location, priceMin, priceMax,
                dateFrom, dateTo, minEventRating, minCompanyRating, Optional.ofNullable(v));
    }
}