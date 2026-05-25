import api from "./api";
import type {
    Area,
    AreaKind,
    DiscountRule,
    DiscountRuleKind,
    Event,
    EventSearchFilters,
    EventStatus,
    EventSummary,
    PurchasePolicy,
    Ticket,
    TicketStatus,
} from "../types/event";

// =============================================================================
// REAL BACKEND CALLS (UC 2.3.1 — Event Search)
//
// `EventController` exposes:
//   POST /api/events/search          — body: EventSearchCriteriaRequest
//   GET  /api/events/{eventId}       — details for a single event
//
// We follow the same pattern as companyService.ts:
//   - import the shared `api` axios client (baseURL already ends in /api)
//   - return `response.data.data` so callers receive the unwrapped payload
//   - keep request field names identical to the backend DTO
// =============================================================================

/**
 * Wire format returned by `EventService.toSummary` on the server. The shape
 * is intentionally flat — one row per event, with everything the search card
 * needs already denormalised (company name, price range, ticket counts).
 */
type EventSummaryResponse = {
    eventId: string;
    companyId: string;
    companyName: string;
    companyRating: number;
    name: string;
    artist: string;
    eventType: string;
    date: string;
    location: string;
    rating: number;
    priceMin: number;
    priceMax: number;
    availableTickets: number;
    totalTickets: number;
};

/**
 * Wire format consumed by `POST /api/events/search`. All fields are optional —
 * the backend treats null as "no filter on this dimension". We never send
 * empty strings; the backend wants nulls or omitted keys to mean "skip".
 */
type EventSearchCriteriaRequest = {
    text: string | null;
    location: string | null;
    priceMin: number | null;
    priceMax: number | null;
    dateFrom: string | null;
    dateTo: string | null;
    minEventRating: number | null;
    minCompanyRating: number | null;
    companyId: string | null;
};

const UUID_PATTERN =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function parseOptionalNumber(raw: string): number | null {
    if (raw.trim() === "") {
        return null;
    }
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
}

function parseOptionalString(raw: string): string | null {
    const trimmed = raw.trim();
    return trimmed === "" ? null : trimmed;
}

/**
 * Convert the UI's string-based filters into the JSON shape the backend's
 * `EventSearchCriteriaRequest` expects. `<input type="date">` produces
 * `YYYY-MM-DD`; we widen those to a full-day window so the LocalDateTime
 * comparison on the server includes the boundary dates.
 */
function buildSearchRequest(
    filters: EventSearchFilters,
): EventSearchCriteriaRequest {
    const companyIdRaw = filters.companyId.trim();
    const companyId = UUID_PATTERN.test(companyIdRaw) ? companyIdRaw : null;

    return {
        text: parseOptionalString(filters.text),
        location: parseOptionalString(filters.location),
        priceMin: parseOptionalNumber(filters.priceMin),
        priceMax: parseOptionalNumber(filters.priceMax),
        dateFrom: filters.dateFrom ? `${filters.dateFrom}T00:00:00` : null,
        dateTo: filters.dateTo ? `${filters.dateTo}T23:59:59` : null,
        minEventRating: parseOptionalNumber(filters.minEventRating),
        minCompanyRating: parseOptionalNumber(filters.minCompanyRating),
        companyId,
    };
}

function toEventSummary(response: EventSummaryResponse): EventSummary {
    return {
        id: response.eventId,
        companyId: response.companyId,
        companyName: response.companyName,
        companyRating: response.companyRating,
        name: response.name ?? "",
        artist: response.artist,
        type: response.eventType,
        date: response.date,
        location: response.location,
        rating: response.rating,
        priceMin: response.priceMin,
        priceMax: response.priceMax,
        availableTickets: response.availableTickets,
        totalTickets: response.totalTickets,
    };
}

/**
 * Fetch search results from the backend. Passing the empty filter object
 * returns every publicly-visible event (UC 2.3 entry view).
 */
export async function searchEvents(
    filters: EventSearchFilters,
): Promise<EventSummary[]> {
    const body = buildSearchRequest(filters);
    const response = await api.post("/events/search", body);
    const rows = (response.data.data ?? []) as EventSummaryResponse[];
    return rows.map(toEventSummary);
}

// =============================================================================
// REAL BACKEND CALL (UC 2.1 — Event Details)
//
// `EventController.getEventDetails` returns the full event payload in a
// single round-trip — company info, status, tags, structured policies, the
// full venue layout and the per-ticket inventory snapshot — so the Event
// Details page can render without any follow-up calls.
// =============================================================================

type AreaResponse = {
    areaId: string;
    kind: AreaKind;
    price: number;
    ticketIds: string[];
};

type TicketResponse = {
    ticketId: string;
    areaId: string;
    status: TicketStatus;
    price: number;
    row: number | null;
    seat: number | null;
};

type PurchaseRuleResponse = {
    id: string;
    kind: "AGE" | "MIN_TICKETS" | "MAX_TICKETS" | "LONE_SEAT";
    minAge: number | null;
    minTickets: number | null;
    maxTickets: number | null;
    allowLoneSeat: boolean | null;
};

type DiscountRuleResponse = {
    id: string;
    kind: DiscountRuleKind;
    fromDate: string;
    toDate: string;
    percent: number;
    requiredTickets: number | null;
    appliedTickets: number | null;
    code: string | null;
};

type EventDetailsResponse = {
    eventId: string;
    companyId: string;
    companyName: string;
    companyRating: number;
    name: string;
    artist: string;
    eventType: string;
    date: string;
    location: string;
    description: string | null;
    tags: string[];
    status: EventStatus;
    rating: number;
    lotteryId: string | null;
    priceMin: number;
    priceMax: number;
    availableTickets: number;
    totalTickets: number;
    areas: AreaResponse[];
    tickets: TicketResponse[];
    purchasePolicy: { rules: PurchaseRuleResponse[] };
    discountPolicy: { rules: DiscountRuleResponse[] };
    effectiveDiscountPolicy?: { rules: DiscountRuleResponse[] };
};

/**
 * The backend Area class doesn't carry a human-readable name (it's a pure
 * priced zone), but the EventDetails / TicketPurchase pages need one for
 * their headings. We synthesize a stable label from the zone kind and the
 * position within its kind, e.g. "Standing area 1" / "Sitting area 2".
 */
function synthesizeAreaName(area: AreaResponse, indexInKind: number): string {
    const prefix = area.kind === "STANDING" ? "Standing area" : "Sitting area";
    return `${prefix} ${indexInKind + 1}`;
}

function toArea(response: AreaResponse, name: string): Area {
    return {
        id: response.areaId,
        name,
        kind: response.kind,
        price: response.price,
        ticketIds: [...response.ticketIds],
    };
}

function toTicket(response: TicketResponse, eventId: string): Ticket {
    const base: Ticket = {
        id: response.ticketId,
        eventId,
        areaId: response.areaId,
        status: response.status,
        price: response.price,
    };
    if (response.row != null) {
        base.row = response.row;
    }
    if (response.seat != null) {
        base.seat = response.seat;
    }
    return base;
}

/**
 * The backend ships purchase rules as a flat list keyed by `kind` (the rule
 * tree is flattened on the server). The frontend `PurchasePolicy` is a
 * single denormalised object, so we fold the list down here. If the spec
 * ever needs to surface composition (And/Or) in the UI, swap this for a
 * tree mapper.
 */
function toPurchasePolicy(rules: PurchaseRuleResponse[]): PurchasePolicy {
    const policy: PurchasePolicy = {};
    for (const rule of rules) {
        switch (rule.kind) {
            case "AGE":
                if (rule.minAge != null) policy.minAge = rule.minAge;
                break;
            case "MIN_TICKETS":
                if (rule.minTickets != null) {
                    policy.minTicketsPerPurchase = rule.minTickets;
                }
                break;
            case "MAX_TICKETS":
                if (rule.maxTickets != null) {
                    policy.maxTicketsPerPurchase = rule.maxTickets;
                }
                break;
            case "LONE_SEAT":
                if (rule.allowLoneSeat != null) {
                    policy.allowLoneSeat = rule.allowLoneSeat;
                }
                break;
        }
    }
    return policy;
}

function toDiscountRule(response: DiscountRuleResponse): DiscountRule {
    const rule: DiscountRule = {
        id: response.id,
        kind: response.kind,
        fromDate: response.fromDate,
        toDate: response.toDate,
        percent: response.percent,
    };
    if (response.requiredTickets != null) {
        rule.requiredTickets = response.requiredTickets;
    }
    if (response.appliedTickets != null) {
        rule.appliedTickets = response.appliedTickets;
    }
    if (response.code != null) {
        rule.code = response.code;
    }
    return rule;
}

function toEventDetails(response: EventDetailsResponse): Event {
    const standingCount = { value: 0 };
    const sittingCount = { value: 0 };

    const areas: Area[] = response.areas.map((area) => {
        const counter = area.kind === "STANDING" ? standingCount : sittingCount;
        const name = synthesizeAreaName(area, counter.value);
        counter.value += 1;

        return toArea(area, name);
    });

    const tickets: Ticket[] = response.tickets.map((ticket) =>
        toTicket(ticket, response.eventId),
    );

    const effectiveDiscountRules =
        response.effectiveDiscountPolicy?.rules ??
        response.discountPolicy.rules ??
        [];

    const event: Event = {
        id: response.eventId,
        name: response.name ?? "",
        companyId: response.companyId,
        companyName: response.companyName,
        date: response.date,
        location: response.location,

        tags: [...response.tags],

        status: response.status,
        artist: response.artist,
        type: response.eventType,
        rating: response.rating,
        companyRating: response.companyRating,
        layout: { areas },
        purchasePolicy: toPurchasePolicy(response.purchasePolicy.rules),
        discountPolicy: {
            rules: effectiveDiscountRules.map(toDiscountRule),
        },
        tickets,
    };

    if (response.lotteryId) {
        event.lotteryId = response.lotteryId;
    }

    if (response.description != null) {
        event.description = response.description;
    }

    return event;
}

/**
 * Fetch the full details of a single event. Returns `null` if the backend
 * reports a 4xx (not found / invalid id) so the page can render its
 * "Event unavailable" empty state. Other errors propagate to the caller.
 */
export async function getEventById(eventId: string): Promise<Event | null> {
    try {
        const response = await api.get(`/events/${eventId}`);
        const data = response.data.data as EventDetailsResponse | null;
        if (!data) {
            return null;
        }
        return toEventDetails(data);
    } catch (error) {
        const status =
            typeof error === "object" && error !== null && "response" in error
                ? (error as { response?: { status?: number } }).response?.status
                : undefined;
        if (status !== undefined && status >= 400 && status < 500) {
            return null;
        }
        throw error;
    }
}

