// Frontend types for the Event aggregate.
// Aligned with the backend domain model in
// Version1/src/main/java/org/example/DomainLayer/EventAggregate/*.java
// (Event, EventStatus, Layout, Area, Ticket, TicketStatus,
//  EventSearchCriteria) and the PolicyManagment package
// (PurchasePolicy, DiscountPolicy and the *Rule / *Discount classes).

// EventStatus enum — see EventStatus.java
export type EventStatus = "ACTIVE" | "CANCELED" | "ENDED";

// TicketStatus enum — see TicketStatus.java
export type TicketStatus = "AVAILABLE" | "RESERVED" | "SOLD";

// Area is abstract on the backend with StandingArea / SittingArea concretes.
export type AreaKind = "STANDING" | "SITTING";

export type Area = {
    id: string;
    name: string;
    kind: AreaKind;
    price: number;
    ticketIds: string[];
};

// Layout (backend: Layout.java) — mapImage + areas list.
export type Layout = {
    mapImage?: string;
    areas: Area[];
};

// Ticket (backend: Ticket.java + Sitting/Standing subclasses).
// row / seat are only set for sitting tickets.
export type Ticket = {
    id: string;
    eventId: string;
    areaId: string;
    status: TicketStatus;
    price: number;
    row?: number;
    seat?: number;
};

// PurchasePolicy summary — derived from the rules added via
// Event.addPurchasePolicy(...) on the backend (AgeRule, MinTicketRule,
// MaxTicketRule, LoneSeatRule).
export type PurchasePolicy = {
    minAge?: number;
    minTicketsPerPurchase?: number;
    maxTicketsPerPurchase?: number;
    allowLoneSeat?: boolean;
};

// DiscountPolicy rules — backed by OvertDiscount / ConditionalDiscount /
// CouponCode on the backend.
export type DiscountRuleKind = "OVERT" | "CONDITIONAL" | "COUPON";

export type DiscountRule = {
    id: string;
    kind: DiscountRuleKind;
    fromDate: string;
    toDate: string;
    percent: number;
    requiredTickets?: number;
    appliedTickets?: number;
    code?: string;
};

export type DiscountPolicy = {
    rules: DiscountRule[];
};

export type Event = {
    id: string;
    name: string;
    companyId: string;
    companyName: string;
    date: string;
    location: string;
    tags: string[];
    status: EventStatus;
    artist: string;
    type: string;
    rating: number;
    companyRating: number;
    lotteryId?: string;
    lotteryWinnersDrawn: boolean;
    layout: Layout;
    purchasePolicy: PurchasePolicy;
    discountPolicy: DiscountPolicy;
    tickets: Ticket[];
    // Free-text description is not in the assignment spec (it lists name,
    // artist, category, date/location, areas, policy and discounts only).
    // We keep it as an optional field so the UI can fall back gracefully
    // if a future iteration adds it server-side.
    description?: string;
};

/**
 * Mirrors the backend EventSummaryDto (UC 2.3.1 / UC 2.3.2 search result row).
 * This is the lightweight shape returned by `POST /api/events/search` —
 * exactly what the Event Search card needs to render, with no follow-up call.
 */
export type EventSummary = {
    id: string;
    companyId: string;
    companyName: string;
    companyRating: number;
    name: string;
    artist: string;
    type: string;
    date: string;
    location: string;
    rating: number;
    status: EventStatus;
    priceMin: number;
    priceMax: number;
    availableTickets: number;
    totalTickets: number;
};

export function formatSummaryPriceRange(summary: EventSummary): string {
    if (summary.priceMin === summary.priceMax) {
        return `${summary.priceMin} NIS`;
    }
    return `${summary.priceMin}–${summary.priceMax} NIS`;
}

// Mirrors EventSearchCriteria.java. All fields are stored as strings so they
// bind directly to controlled <input> elements and are normalised at filter
// time.
export type EventSearchFilters = {
    text: string;
    location: string;
    priceMin: string;
    priceMax: string;
    dateFrom: string;
    dateTo: string;
    minEventRating: string;
    minCompanyRating: string;
    companyId: string;
};

export const EMPTY_EVENT_SEARCH_FILTERS: EventSearchFilters = {
    text: "",
    location: "",
    priceMin: "",
    priceMax: "",
    dateFrom: "",
    dateTo: "",
    minEventRating: "",
    minCompanyRating: "",
    companyId: "",
};

export function getAvailableTicketsCount(event: Event): number {
    return event.tickets.filter((ticket) => ticket.status === "AVAILABLE").length;
}

export function getPriceRange(event: Event): { min: number; max: number } {
    const prices = event.layout.areas.map((area) => area.price);
    if (prices.length === 0) {
        return { min: 0, max: 0 };
    }
    return {
        min: Math.min(...prices),
        max: Math.max(...prices),
    };
}

// Mirrors Event.isPubliclyVisible(): only ACTIVE events are bookable from
// the catalog.
export function isEventBookable(event: Event): boolean {
    return event.status === "ACTIVE";
}
