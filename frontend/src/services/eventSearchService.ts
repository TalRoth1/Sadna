import type { Event, EventSearchFilters } from "../types/event";

const mockEvents: Event[] = [
    {
        id: "evt-1",
        name: "Summer Music Festival",
        artist: "Various Artists",
        category: "Festival",
        eventDate: "2026-06-20T20:30:00",
        location: "Tel Aviv Park",
        priceFrom: 120,
        priceTo: 320,
        rating: 4.6,
        productionCompany: "Live Nation IL",
        tags: ["outdoor", "summer", "music"],
    },
    {
        id: "evt-2",
        name: "Standup Night",
        artist: "Shahar Hason",
        category: "Live Show",
        eventDate: "2026-04-07T21:00:00",
        location: "Haifa Theater",
        priceFrom: 90,
        priceTo: 160,
        rating: 4.2,
        productionCompany: "Zappa Group",
        tags: ["comedy", "standup"],
    },
    {
        id: "evt-3",
        name: "Hamlet",
        artist: "Cameri Ensemble",
        category: "Theater",
        eventDate: "2026-05-12T19:00:00",
        location: "Cameri Theater, Tel Aviv",
        priceFrom: 180,
        priceTo: 260,
        rating: 4.4,
        productionCompany: "Cameri Productions",
        tags: ["shakespeare", "drama"],
    },
    {
        id: "evt-4",
        name: "AI & Cloud Conference 2026",
        artist: "Keynote: Dr. Yossi Matias",
        category: "Conference",
        eventDate: "2026-09-03T09:00:00",
        location: "Expo Tel Aviv",
        priceFrom: 350,
        priceTo: 850,
        rating: 4.0,
        productionCompany: "TechEvents Israel",
        tags: ["ai", "cloud", "tech"],
    },
    {
        id: "evt-5",
        name: "Jazz on the Rooftop",
        artist: "Avishai Cohen Trio",
        category: "Live Show",
        eventDate: "2026-07-15T22:00:00",
        location: "Jerusalem",
        priceFrom: 140,
        priceTo: 240,
        rating: 4.8,
        productionCompany: "Live Nation IL",
        tags: ["jazz", "rooftop"],
    },
    {
        id: "evt-6",
        name: "Indie Beats Festival",
        artist: "Various Artists",
        category: "Festival",
        eventDate: "2026-08-22T18:00:00",
        location: "Eilat",
        priceFrom: 200,
        priceTo: 480,
        rating: 3.9,
        productionCompany: "Zappa Group",
        tags: ["indie", "beach", "festival"],
    },
    {
        id: "evt-7",
        name: "Romeo and Juliet",
        artist: "Habima National Theater",
        category: "Theater",
        eventDate: "2026-03-30T20:00:00",
        location: "Habima Square, Tel Aviv",
        priceFrom: 160,
        priceTo: 240,
        rating: 4.1,
        productionCompany: "Habima Productions",
        tags: ["shakespeare", "classic"],
    },
    {
        id: "evt-8",
        name: "Frontend Developers Meetup",
        artist: "Community Speakers",
        category: "Conference",
        eventDate: "2026-05-28T17:30:00",
        location: "Beer Sheva",
        priceFrom: 0,
        priceTo: 50,
        rating: 4.3,
        productionCompany: "TechEvents Israel",
        tags: ["react", "frontend", "meetup"],
    },
];

function matchesQuery(event: Event, rawQuery: string): boolean {
    const query = rawQuery.trim().toLowerCase();
    if (!query) {
        return true;
    }

    const haystack = [
        event.name,
        event.artist,
        event.category,
        event.location,
        ...event.tags,
    ]
        .join(" ")
        .toLowerCase();

    return haystack.includes(query);
}

function matchesPriceRange(
    event: Event,
    priceMin: string,
    priceMax: string,
): boolean {
    const min = priceMin === "" ? null : Number(priceMin);
    const max = priceMax === "" ? null : Number(priceMax);

    if (min !== null && !Number.isNaN(min) && event.priceTo < min) {
        return false;
    }

    if (max !== null && !Number.isNaN(max) && event.priceFrom > max) {
        return false;
    }

    return true;
}

function matchesDateRange(
    event: Event,
    dateFrom: string,
    dateTo: string,
): boolean {
    const eventTime = new Date(event.eventDate).getTime();
    if (Number.isNaN(eventTime)) {
        return false;
    }

    if (dateFrom) {
        const fromTime = new Date(dateFrom).getTime();
        if (!Number.isNaN(fromTime) && eventTime < fromTime) {
            return false;
        }
    }

    if (dateTo) {
        // Include the entire "dateTo" day.
        const toTime = new Date(`${dateTo}T23:59:59`).getTime();
        if (!Number.isNaN(toTime) && eventTime > toTime) {
            return false;
        }
    }

    return true;
}

function matchesSubstring(value: string, filter: string): boolean {
    const normalized = filter.trim().toLowerCase();
    if (!normalized) {
        return true;
    }
    return value.toLowerCase().includes(normalized);
}

export function searchEvents(
    events: Event[],
    filters: EventSearchFilters,
): Event[] {
    const minRating =
        filters.minRating === "" ? null : Number(filters.minRating);

    return events.filter((event) => {
        if (!matchesQuery(event, filters.query)) {
            return false;
        }

        if (filters.category && event.category !== filters.category) {
            return false;
        }

        if (!matchesPriceRange(event, filters.priceMin, filters.priceMax)) {
            return false;
        }

        if (!matchesDateRange(event, filters.dateFrom, filters.dateTo)) {
            return false;
        }

        if (!matchesSubstring(event.location, filters.location)) {
            return false;
        }

        if (
            !matchesSubstring(event.productionCompany, filters.productionCompany)
        ) {
            return false;
        }

        if (
            minRating !== null &&
            !Number.isNaN(minRating) &&
            event.rating < minRating
        ) {
            return false;
        }

        return true;
    });
}

// TODO: Replace mock data with the real communication layer once the protocol/API is implemented.
// This function should request the event catalog (optionally scoped to a production company).
export async function getEvents(): Promise<Event[]> {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve(mockEvents);
        }, 400);
    });
}
