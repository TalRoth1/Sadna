import { useEffect, useState } from "react";
import type { ChangeEvent } from "react";
import { searchEvents } from "../../services/eventSearchService";
import {
    EMPTY_EVENT_SEARCH_FILTERS,
    formatSummaryPriceRange,
    type EventSearchFilters,
    type EventSummary,
} from "../../types/event";
import "./EventSearch.css";

// The page is bound to the assignment requirement:
//   חיפוש גלובלי (ללא התמקדות בחברת הפקה ספציפית) — חיפוש לפי
//   שם האירוע, שם האמן, קטגוריה או מילות מפתח, וסינון לפי טווח
//   מחירים, טווח תאריכים, מיקום/אזור ודירוג אירוע/חברת הפקה.
//
// The single text input maps to the backend's "text" filter, which scans
// the event name, artist, type and tags. Every other input maps 1:1 to a
// dedicated EventSearchCriteria slot on the server.

type EventSearchPageProps = {
    onSelectEvent: (eventId: string) => void;
};

// Debounce window for live filtering. Short enough to feel responsive,
// long enough to avoid hammering the backend on every keystroke.
const SEARCH_DEBOUNCE_MS = 300;

function formatEventDate(date: string) {
    const eventDate = new Date(date);

    if (Number.isNaN(eventDate.getTime())) {
        return "Invalid date";
    }

    return eventDate.toLocaleString("he-IL", {
        dateStyle: "short",
        timeStyle: "short",
    });
}

type EventCardProps = {
    event: EventSummary;
    onSelect: (event: EventSummary) => void;
};

function EventCard({ event, onSelect }: EventCardProps) {
    const availableTickets = event.availableTickets;
    const displayName = event.name.trim() === "" ? "Untitled event" : event.name;

    return (
        <article
            className="event-card"
            role="button"
            tabIndex={0}
            onClick={() => onSelect(event)}
            onKeyDown={(keyEvent) => {
                if (keyEvent.key === "Enter" || keyEvent.key === " ") {
                    keyEvent.preventDefault();
                    onSelect(event);
                }
            }}
        >
            <div className="event-card-main">
                <div className="event-card-heading">
                    <h2>{displayName}</h2>
                    <span className="event-category-badge">{event.type}</span>
                </div>
                <p className="event-artist">{event.artist}</p>
                <p className="event-meta">{formatEventDate(event.date)}</p>
                <p className="event-meta">{event.location}</p>
                <p className="event-meta event-company">
                    Production: {event.companyName}
                </p>
            </div>

            <div className="event-card-side">
                <span className="event-price">{formatSummaryPriceRange(event)}</span>
                <span className="event-rating" aria-label={`Rating ${event.rating}`}>
                    ★ {event.rating.toFixed(1)}
                </span>
                <span className="event-availability">
                    {availableTickets > 0
                        ? `${availableTickets} tickets left`
                        : "Sold out"}
                </span>
            </div>
        </article>
    );
}

type SearchFiltersFormProps = {
    filters: EventSearchFilters;
    onChange: (filters: EventSearchFilters) => void;
    onReset: () => void;
};

function SearchFiltersForm({
    filters,
    onChange,
    onReset,
}: SearchFiltersFormProps) {
    function updateField<K extends keyof EventSearchFilters>(
        key: K,
        value: EventSearchFilters[K],
    ) {
        onChange({ ...filters, [key]: value });
    }

    function handleInputChange(key: keyof EventSearchFilters) {
        return (changeEvent: ChangeEvent<HTMLInputElement>) => {
            updateField(key, changeEvent.target.value);
        };
    }

    return (
        <section className="event-filters" aria-label="Event search filters">
            <div className="event-filters-row event-filters-row--primary">
                <label className="event-filter event-filter--grow">
                    <span>Search</span>
                    <input
                        type="search"
                        placeholder="Event, artist, category or keywords"
                        value={filters.text}
                        onChange={handleInputChange("text")}
                    />
                </label>

                <label className="event-filter event-filter--grow">
                    <span>Production company (optional)</span>
                    <input
                        type="text"
                        placeholder="Scope to a company id"
                        value={filters.companyId}
                        onChange={handleInputChange("companyId")}
                    />
                </label>
            </div>

            <div className="event-filters-row">
                <label className="event-filter">
                    <span>From date</span>
                    <input
                        type="date"
                        value={filters.dateFrom}
                        onChange={handleInputChange("dateFrom")}
                    />
                </label>

                <label className="event-filter">
                    <span>To date</span>
                    <input
                        type="date"
                        value={filters.dateTo}
                        onChange={handleInputChange("dateTo")}
                    />
                </label>

                <label className="event-filter">
                    <span>Min price</span>
                    <input
                        type="number"
                        min={0}
                        placeholder="0"
                        value={filters.priceMin}
                        onChange={handleInputChange("priceMin")}
                    />
                </label>

                <label className="event-filter">
                    <span>Max price</span>
                    <input
                        type="number"
                        min={0}
                        placeholder="∞"
                        value={filters.priceMax}
                        onChange={handleInputChange("priceMax")}
                    />
                </label>
            </div>

            <div className="event-filters-row">
                <label className="event-filter event-filter--grow">
                    <span>Location / region</span>
                    <input
                        type="text"
                        placeholder="e.g. Tel Aviv"
                        value={filters.location}
                        onChange={handleInputChange("location")}
                    />
                </label>

                <label className="event-filter">
                    <span>Min event rating</span>
                    <input
                        type="number"
                        min={0}
                        max={5}
                        step={0.1}
                        placeholder="0–5"
                        value={filters.minEventRating}
                        onChange={handleInputChange("minEventRating")}
                    />
                </label>

                <label className="event-filter">
                    <span>Min company rating</span>
                    <input
                        type="number"
                        min={0}
                        max={5}
                        step={0.1}
                        placeholder="0–5"
                        value={filters.minCompanyRating}
                        onChange={handleInputChange("minCompanyRating")}
                    />
                </label>
            </div>

            <div className="event-filters-actions">
                <button
                    type="button"
                    className="event-filters-reset"
                    onClick={onReset}
                >
                    Reset filters
                </button>
            </div>
        </section>
    );
}

export default function EventSearchPage({
    onSelectEvent,
}: EventSearchPageProps) {
    const [events, setEvents] = useState<EventSummary[]>([]);
    const [filters, setFilters] = useState<EventSearchFilters>(
        EMPTY_EVENT_SEARCH_FILTERS,
    );
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState("");

    // Re-run the search whenever the filters change. We debounce so the
    // backend isn't called on every keystroke, and we use an "isStale" flag
    // to ignore responses for filter sets that the user has since changed.
    useEffect(() => {
        let isStale = false;

        const timer = window.setTimeout(async () => {
            try {
                setIsLoading(true);
                setErrorMessage("");

                const results = await searchEvents(filters);
                if (!isStale) {
                    setEvents(results);
                }
            } catch {
                if (!isStale) {
                    setErrorMessage("Failed to load events.");
                }
            } finally {
                if (!isStale) {
                    setIsLoading(false);
                }
            }
        }, SEARCH_DEBOUNCE_MS);

        return () => {
            isStale = true;
            window.clearTimeout(timer);
        };
    }, [filters]);

    const hasActiveFilters = Object.values(filters).some(
        (value) => value !== "",
    );

    function handleResetFilters() {
        setFilters(EMPTY_EVENT_SEARCH_FILTERS);
    }

    function handleEventSelect(event: EventSummary) {
        onSelectEvent(event.id);
    }

    return (
        <main className="app-page event-search-page">
            <section className="page-header">
                <h1>Find Your Next Event</h1>
                <p>
                    Search the catalog by name, artist, category or keywords,
                    then narrow the results down by date range, price range,
                    location and event/company rating.
                </p>
            </section>

            <SearchFiltersForm
                filters={filters}
                onChange={setFilters}
                onReset={handleResetFilters}
            />

            {isLoading && (
                <section className="empty-state">
                    <h2>Loading events…</h2>
                    <p>Please wait while we load the event catalog.</p>
                </section>
            )}

            {!isLoading && errorMessage && (
                <section className="empty-state">
                    <h2>Something went wrong</h2>
                    <p>{errorMessage}</p>
                </section>
            )}

            {!isLoading && !errorMessage && events.length === 0 && (
                <section className="empty-state">
                    <h2>No matching events found</h2>
                    <p>
                        Try adjusting the search term or resetting the filters
                        to broaden your results.
                    </p>
                    {hasActiveFilters && (
                        <button
                            type="button"
                            className="event-filters-reset"
                            onClick={handleResetFilters}
                        >
                            Reset filters
                        </button>
                    )}
                </section>
            )}

            {!isLoading && !errorMessage && events.length > 0 && (
                <section className="event-results">
                    <header className="event-results-header">
                        <h2>Results</h2>
                        <span className="event-results-count">
                            {events.length} event
                            {events.length === 1 ? "" : "s"}
                        </span>
                    </header>

                    <div className="event-list">
                        {events.map((event) => (
                            <EventCard
                                key={event.id}
                                event={event}
                                onSelect={handleEventSelect}
                            />
                        ))}
                    </div>
                </section>
            )}
        </main>
    );
}
