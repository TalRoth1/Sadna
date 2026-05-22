import api from "./api";
import type {
    Area,
    Event,
    EventSearchFilters,
    EventSummary,
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
// LEGACY MOCK (still used by the EventDetails screen).
//
// The Event Search page no longer reads from this dataset — it queries the
// backend directly. We keep the mock in place so `getEventById` (consumed by
// the EventDetails screen, which has not yet been migrated to the real API)
// continues to work. Remove this block once EventDetails is integrated.
// =============================================================================

function makeAvailableTickets(
    eventId: string,
    area: Area,
    count: number,
    pricePerTicket: number,
    startIndex: number,
): Ticket[] {
    const tickets: Ticket[] = [];
    for (let index = 0; index < count; index += 1) {
        tickets.push({
            id: `ticket-${eventId}-${area.id}-${startIndex + index}`,
            eventId,
            areaId: area.id,
            status: "AVAILABLE",
            price: pricePerTicket,
        });
    }
    return tickets;
}

function makeSittingTickets(
    eventId: string,
    area: Area,
    rows: number,
    seatsPerRow: number,
    options: {
        reserved?: Array<[number, number]>;
        sold?: Array<[number, number]>;
    } = {},
): Ticket[] {
    const reservedKeys = new Set(
        (options.reserved ?? []).map(([row, seat]) => `${row}-${seat}`),
    );
    const soldKeys = new Set(
        (options.sold ?? []).map(([row, seat]) => `${row}-${seat}`),
    );

    const tickets: Ticket[] = [];
    for (let row = 1; row <= rows; row += 1) {
        for (let seat = 1; seat <= seatsPerRow; seat += 1) {
            const key = `${row}-${seat}`;
            let status: TicketStatus = "AVAILABLE";
            if (soldKeys.has(key)) {
                status = "SOLD";
            } else if (reservedKeys.has(key)) {
                status = "RESERVED";
            }
            tickets.push({
                id: `ticket-${eventId}-${area.id}-r${row}s${seat}`,
                eventId,
                areaId: area.id,
                status,
                price: area.price,
                row,
                seat,
            });
        }
    }
    return tickets;
}

const summerFestivalAreas: Area[] = [
    { id: "area-evt-1-pit", name: "Pit", kind: "STANDING", price: 320, ticketIds: [] },
    { id: "area-evt-1-floor", name: "Floor", kind: "STANDING", price: 220, ticketIds: [] },
    { id: "area-evt-1-back", name: "Back lawn", kind: "STANDING", price: 120, ticketIds: [] },
];

const standupNightAreas: Area[] = [
    { id: "area-evt-2-stalls", name: "Stalls", kind: "SITTING", price: 160, ticketIds: [] },
    { id: "area-evt-2-balcony", name: "Balcony", kind: "SITTING", price: 90, ticketIds: [] },
];

const hamletAreas: Area[] = [
    { id: "area-evt-3-orchestra", name: "Orchestra", kind: "SITTING", price: 260, ticketIds: [] },
    { id: "area-evt-3-mezzanine", name: "Mezzanine", kind: "SITTING", price: 180, ticketIds: [] },
];

const aiCloudAreas: Area[] = [
    { id: "area-evt-4-keynote", name: "Keynote pass", kind: "SITTING", price: 850, ticketIds: [] },
    { id: "area-evt-4-general", name: "General admission", kind: "STANDING", price: 350, ticketIds: [] },
];

const jazzRooftopAreas: Area[] = [
    { id: "area-evt-5-window", name: "Window seats", kind: "SITTING", price: 240, ticketIds: [] },
    { id: "area-evt-5-bar", name: "Bar", kind: "STANDING", price: 140, ticketIds: [] },
];

const indieFestAreas: Area[] = [
    { id: "area-evt-6-vip", name: "VIP", kind: "STANDING", price: 480, ticketIds: [] },
    { id: "area-evt-6-ga", name: "General", kind: "STANDING", price: 200, ticketIds: [] },
];

const meetupAreas: Area[] = [
    { id: "area-evt-7-room", name: "Main room", kind: "SITTING", price: 50, ticketIds: [] },
];

const mockEvents: Event[] = [
    {
        id: "evt-1",
        name: "Summer Music Festival",
        companyId: "co-live-nation",
        companyName: "Live Nation IL",
        date: "2026-06-20T20:30:00",
        location: "Tel Aviv Park",
        tags: ["outdoor", "summer", "music"],
        status: "ACTIVE",
        artist: "Various Artists",
        type: "Festival",
        rating: 4.6,
        companyRating: 4.5,
        layout: { areas: summerFestivalAreas },
        purchasePolicy: {
            minAge: 16,
            minTicketsPerPurchase: 1,
            maxTicketsPerPurchase: 6,
            allowLoneSeat: true,
        },
        discountPolicy: {
            rules: [
                {
                    id: "disc-evt-1-early",
                    kind: "OVERT",
                    fromDate: "2026-04-01",
                    toDate: "2026-05-15",
                    percent: 10,
                },
            ],
        },
        tickets: [
            ...makeAvailableTickets("evt-1", summerFestivalAreas[0], 80, 320, 0),
            ...makeAvailableTickets("evt-1", summerFestivalAreas[1], 240, 220, 0),
            ...makeAvailableTickets("evt-1", summerFestivalAreas[2], 600, 120, 0),
        ],
        description:
            "A weekend-long open-air festival showcasing the best of Israeli " +
            "and international live music. Multiple stages, food trucks and a " +
            "late-night silent disco.",
    },
    {
        id: "evt-2",
        name: "Standup Night",
        companyId: "co-zappa",
        companyName: "Zappa Group",
        date: "2026-04-07T21:00:00",
        location: "Haifa Theater",
        tags: ["comedy", "standup"],
        status: "ACTIVE",
        artist: "Shahar Hason",
        type: "Live Show",
        rating: 4.2,
        companyRating: 4.0,
        layout: { areas: standupNightAreas },
        purchasePolicy: {
            minAge: 18,
            minTicketsPerPurchase: 1,
            maxTicketsPerPurchase: 4,
            allowLoneSeat: true,
        },
        discountPolicy: { rules: [] },
        tickets: [
            ...makeSittingTickets("evt-2", standupNightAreas[0], 8, 12, {
                sold: [
                    [1, 1], [1, 2], [2, 5], [2, 6], [3, 7], [5, 4], [7, 11],
                ],
                reserved: [[4, 8], [6, 2]],
            }),
            ...makeSittingTickets("evt-2", standupNightAreas[1], 5, 10, {
                sold: [[1, 5], [1, 6]],
                reserved: [[3, 9]],
            }),
        ],
        description:
            "An intimate set of new material from one of Israel's busiest " +
            "comedians. Strict 18+ door policy.",
    },
    {
        id: "evt-3",
        name: "Hamlet",
        companyId: "co-cameri",
        companyName: "Cameri Productions",
        date: "2026-05-12T19:00:00",
        location: "Cameri Theater, Tel Aviv",
        tags: ["shakespeare", "drama"],
        status: "ACTIVE",
        artist: "Cameri Ensemble",
        type: "Theater",
        rating: 4.4,
        companyRating: 4.3,
        layout: { areas: hamletAreas },
        purchasePolicy: {
            minTicketsPerPurchase: 1,
            maxTicketsPerPurchase: 6,
            allowLoneSeat: false,
        },
        discountPolicy: {
            rules: [
                {
                    id: "disc-evt-3-coupon",
                    kind: "COUPON",
                    fromDate: "2026-03-01",
                    toDate: "2026-05-12",
                    percent: 15,
                    code: "CAMERI15",
                },
            ],
        },
        tickets: [
            ...makeSittingTickets("evt-3", hamletAreas[0], 10, 14, {
                sold: [
                    [1, 1], [1, 7], [2, 3], [2, 4], [3, 8], [5, 5], [5, 6],
                    [7, 11], [10, 1], [10, 14],
                ],
                reserved: [[4, 7], [6, 10], [8, 2]],
            }),
            ...makeSittingTickets("evt-3", hamletAreas[1], 8, 14, {
                sold: [[1, 1], [1, 2], [2, 7], [5, 11], [7, 3]],
                reserved: [[3, 8], [6, 9]],
            }),
        ],
        description:
            "A modern-dress staging of Shakespeare's Hamlet by the Cameri " +
            "Ensemble. Hebrew with English surtitles.",
    },
    {
        id: "evt-4",
        name: "AI & Cloud Conference 2026",
        companyId: "co-techevents",
        companyName: "TechEvents Israel",
        date: "2026-09-03T09:00:00",
        location: "Expo Tel Aviv",
        tags: ["ai", "cloud", "tech"],
        status: "ACTIVE",
        artist: "Keynote: Dr. Yossi Matias",
        type: "Conference",
        rating: 4.0,
        companyRating: 4.2,
        lotteryId: "lottery-evt-4",
        layout: { areas: aiCloudAreas },
        purchasePolicy: {
            minTicketsPerPurchase: 1,
            maxTicketsPerPurchase: 2,
            allowLoneSeat: true,
        },
        discountPolicy: { rules: [] },
        tickets: [
            ...makeSittingTickets("evt-4", aiCloudAreas[0], 6, 12, {
                sold: [[1, 1], [1, 12], [2, 6], [2, 7]],
                reserved: [[3, 4]],
            }),
            ...makeAvailableTickets("evt-4", aiCloudAreas[1], 800, 350, 0),
        ],
        description:
            "Two days of talks and workshops on cloud-native AI, with " +
            "tracks for engineers, researchers and decision makers.",
    },
    {
        id: "evt-5",
        name: "Jazz on the Rooftop",
        companyId: "co-live-nation",
        companyName: "Live Nation IL",
        date: "2026-07-15T22:00:00",
        location: "Jerusalem",
        tags: ["jazz", "rooftop"],
        status: "ACTIVE",
        artist: "Avishai Cohen Trio",
        type: "Live Show",
        rating: 4.8,
        companyRating: 4.5,
        layout: { areas: jazzRooftopAreas },
        purchasePolicy: {
            minTicketsPerPurchase: 1,
            maxTicketsPerPurchase: 4,
            allowLoneSeat: true,
        },
        discountPolicy: { rules: [] },
        tickets: [
            ...makeSittingTickets("evt-5", jazzRooftopAreas[0], 4, 6, {
                sold: [
                    [1, 1], [1, 2], [1, 3], [1, 4], [1, 5], [1, 6],
                    [2, 1], [2, 2], [2, 3], [2, 4], [2, 5], [2, 6],
                    [3, 1], [3, 2], [3, 3], [3, 4], [3, 5], [3, 6],
                    [4, 1], [4, 2], [4, 3], [4, 4], [4, 5], [4, 6],
                ],
            }),
            ...makeAvailableTickets("evt-5", jazzRooftopAreas[1], 0, 140, 0),
        ],
        description:
            "Late-night jazz under the stars with the Avishai Cohen Trio. " +
            "Limited capacity rooftop venue.",
    },
    {
        id: "evt-6",
        name: "Indie Beats Festival",
        companyId: "co-zappa",
        companyName: "Zappa Group",
        date: "2026-08-22T18:00:00",
        location: "Eilat",
        tags: ["indie", "beach", "festival"],
        status: "CANCELED",
        artist: "Various Artists",
        type: "Festival",
        rating: 3.9,
        companyRating: 4.0,
        layout: { areas: indieFestAreas },
        purchasePolicy: { minTicketsPerPurchase: 1 },
        discountPolicy: { rules: [] },
        tickets: [
            ...makeAvailableTickets("evt-6", indieFestAreas[0], 100, 480, 0),
            ...makeAvailableTickets("evt-6", indieFestAreas[1], 600, 200, 0),
        ],
        description:
            "Cancelled due to logistical issues with the venue. Refunds " +
            "are processed automatically through the original payment method.",
    },
    {
        id: "evt-7",
        name: "Frontend Developers Meetup",
        companyId: "co-techevents",
        companyName: "TechEvents Israel",
        date: "2026-05-28T17:30:00",
        location: "Beer Sheva",
        tags: ["react", "frontend", "meetup"],
        status: "ACTIVE",
        artist: "Community Speakers",
        type: "Conference",
        rating: 4.3,
        companyRating: 4.2,
        layout: { areas: meetupAreas },
        purchasePolicy: {
            minTicketsPerPurchase: 1,
            maxTicketsPerPurchase: 2,
            allowLoneSeat: true,
        },
        discountPolicy: { rules: [] },
        tickets: [
            ...makeSittingTickets("evt-7", meetupAreas[0], 6, 8, {
                sold: [[1, 1], [1, 2]],
                reserved: [[3, 5], [3, 6]],
            }),
        ],
        description:
            "A community meetup focused on the latest in the React " +
            "ecosystem. Pizza included.",
    },
];

// TODO: Replace with a real GET /events/{id} call once the EventDetails
// screen is migrated. The Event Search page no longer relies on this.
export async function getEventById(eventId: string): Promise<Event | null> {
    return new Promise((resolve) => {
        setTimeout(() => {
            const event = mockEvents.find((candidate) => candidate.id === eventId);
            resolve(event ?? null);
        }, 300);
    });
}
