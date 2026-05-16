export type EventCategory =
    | "Live Show"
    | "Theater"
    | "Festival"
    | "Conference";

export type Event = {
    id: string;
    name: string;
    artist: string;
    category: EventCategory;
    eventDate: string;
    location: string;
    priceFrom: number;
    priceTo: number;
    rating: number;
    productionCompany: string;
    tags: string[];
};

export type EventSearchFilters = {
    query: string;
    category: EventCategory | "";
    priceMin: string;
    priceMax: string;
    dateFrom: string;
    dateTo: string;
    location: string;
    minRating: string;
    productionCompany: string;
};

export const EMPTY_EVENT_SEARCH_FILTERS: EventSearchFilters = {
    query: "",
    category: "",
    priceMin: "",
    priceMax: "",
    dateFrom: "",
    dateTo: "",
    location: "",
    minRating: "",
    productionCompany: "",
};
