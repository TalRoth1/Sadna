import { useEffect, useMemo, useState } from "react";
import type { ChangeEvent } from "react";
import { getEvents, searchEvents } from "../../services/eventSearchService";
import {
    EMPTY_EVENT_SEARCH_FILTERS,
    type Event,
    type EventCategory,
    type EventSearchFilters,
} from "../../types/event";
import "./EventSearch.css";

const CATEGORY_OPTIONS: EventCategory[] = [
    "Live Show",
    "Theater",
    "Festival",
    "Conference",
];

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

function formatPriceRange(priceFrom: number, priceTo: number) {
    if (priceFrom === priceTo) {
        return `${priceFrom} NIS`;
    }
    return `${priceFrom}–${priceTo} NIS`;
}

type EventCardProps = {
    event: Event;
    onSelect: (event: Event) => void;
};

function EventCard({ event, onSelect }: EventCardProps) {
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
                    <h2>{event.name}</h2>
                    <span className="event-category-badge">{event.category}</span>
                </div>
                <p className="event-artist">{event.artist}</p>
                <p className="event-meta">{formatEventDate(event.eventDate)}</p>
                <p className="event-meta">{event.location}</p>
                <p className="event-meta event-company">
                    Production: {event.productionCompany}
                </p>
            </div>

            <div className="event-card-side">
                <span className="event-price">
                    {formatPriceRange(event.priceFrom, event.priceTo)}
                </span>
                <span className="event-rating" aria-label={`Rating ${event.rating}`}>
                    ★ {event.rating.toFixed(1)}
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

    function handleCategoryChange(
        changeEvent: ChangeEvent<HTMLSelectElement>,
    ) {
        const nextValue = changeEvent.target.value as EventCategory | "";
        updateField("category", nextValue);
    }

    return (
        <section className="event-filters" aria-label="Event search filters">
            <div className="event-filters-row event-filters-row--primary">
                <label className="event-filter event-filter--grow">
                    <span>Search</span>
                    <input
                        type="search"
                        placeholder="Event, artist or keywords"
                        value={filters.query}
                        onChange={handleInputChange("query")}
                    />
                </label>

                <label className="event-filter">
                    <span>Category</span>
                    <select
                        value={filters.category}
                        onChange={handleCategoryChange}
                    >
                        <option value="">All categories</option>
                        {CATEGORY_OPTIONS.map((category) => (
                            <option key={category} value={category}>
                                {category}
                            </option>
                        ))}
                    </select>
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
                    <span>Min rating</span>
                    <input
                        type="number"
                        min={0}
                        max={5}
                        step={0.1}
                        placeholder="0–5"
                        value={filters.minRating}
                        onChange={handleInputChange("minRating")}
                    />
                </label>

                <label className="event-filter event-filter--grow">
                    <span>Production company (optional)</span>
                    <input
                        type="text"
                        placeholder="Scope to a company"
                        value={filters.productionCompany}
                        onChange={handleInputChange("productionCompany")}
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

export default function EventSearchPage() {
    const [events, setEvents] = useState<Event[]>([]);
    const [filters, setFilters] = useState<EventSearchFilters>(
        EMPTY_EVENT_SEARCH_FILTERS,
    );
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        async function loadEvents() {
            try {
                setIsLoading(true);
                setErrorMessage("");

                const eventCatalog = await getEvents();
                setEvents(eventCatalog);
            } catch {
                setErrorMessage("Failed to load events.");
            } finally {
                setIsLoading(false);
            }
        }

        loadEvents();
    }, []);

    const visibleEvents = useMemo(
        () => searchEvents(events, filters),
        [events, filters],
    );

    const hasActiveFilters = useMemo(
        () =>
            Object.entries(filters).some(([, value]) => value !== ""),
        [filters],
    );

    function handleResetFilters() {
        setFilters(EMPTY_EVENT_SEARCH_FILTERS);
    }

    function handleEventSelect(event: Event) {
        // TODO: Replace with real navigation to the event details screen
        // once the routing layer is introduced.
        console.info("Selected event:", event.id);
    }

    return (
        <main className="event-search-page">
            <section className="page-header">
                <h1>Find Your Next Event</h1>
                <p>
                    Search the catalog by name, artist or keywords, then narrow
                    the results down to your preferred dates, price and location.
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

            {!isLoading && !errorMessage && visibleEvents.length === 0 && (
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

            {!isLoading && !errorMessage && visibleEvents.length > 0 && (
                <section className="event-results">
                    <header className="event-results-header">
                        <h2>Results</h2>
                        <span className="event-results-count">
                            {visibleEvents.length} event
                            {visibleEvents.length === 1 ? "" : "s"}
                        </span>
                    </header>

                    <div className="event-list">
                        {visibleEvents.map((event) => (
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
