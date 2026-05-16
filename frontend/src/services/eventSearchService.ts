import type {
    Area,
    Event,
    EventSearchFilters,
    Ticket,
} from "../types/event";
import { getPriceRange } from "../types/event";

// Helpers to mint the mock dataset. Real backend uses java.util.UUID for
// every id; here we just produce stable strings so React keys are sensible.
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

const summerFestivalAreas: Area[] = [
    {
        id: "area-evt-1-pit",
        name: "Pit",
        kind: "STANDING",
        price: 320,
        ticketIds: [],
    },
    {
        id: "area-evt-1-floor",
        name: "Floor",
        kind: "STANDING",
        price: 220,
        ticketIds: [],
    },
    {
        id: "area-evt-1-back",
        name: "Back lawn",
        kind: "STANDING",
        price: 120,
        ticketIds: [],
    },
];

const standupNightAreas: Area[] = [
    {
        id: "area-evt-2-stalls",
        name: "Stalls",
        kind: "SITTING",
        price: 160,
        ticketIds: [],
    },
    {
        id: "area-evt-2-balcony",
        name: "Balcony",
        kind: "SITTING",
        price: 90,
        ticketIds: [],
    },
];

const hamletAreas: Area[] = [
    {
        id: "area-evt-3-orchestra",
        name: "Orchestra",
        kind: "SITTING",
        price: 260,
        ticketIds: [],
    },
    {
        id: "area-evt-3-mezzanine",
        name: "Mezzanine",
        kind: "SITTING",
        price: 180,
        ticketIds: [],
    },
];

const aiCloudAreas: Area[] = [
    {
        id: "area-evt-4-keynote",
        name: "Keynote pass",
        kind: "SITTING",
        price: 850,
        ticketIds: [],
    },
    {
        id: "area-evt-4-general",
        name: "General admission",
        kind: "STANDING",
        price: 350,
        ticketIds: [],
    },
];

const jazzRooftopAreas: Area[] = [
    {
        id: "area-evt-5-window",
        name: "Window seats",
        kind: "SITTING",
        price: 240,
        ticketIds: [],
    },
    {
        id: "area-evt-5-bar",
        name: "Bar",
        kind: "STANDING",
        price: 140,
        ticketIds: [],
    },
];

const indieFestAreas: Area[] = [
    {
        id: "area-evt-6-vip",
        name: "VIP",
        kind: "STANDING",
        price: 480,
        ticketIds: [],
    },
    {
        id: "area-evt-6-ga",
        name: "General",
        kind: "STANDING",
        price: 200,
        ticketIds: [],
    },
];

const meetupAreas: Area[] = [
    {
        id: "area-evt-7-room",
        name: "Main room",
        kind: "SITTING",
        price: 50,
        ticketIds: [],
    },
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
            ...makeAvailableTickets("evt-2", standupNightAreas[0], 140, 160, 0),
            ...makeAvailableTickets("evt-2", standupNightAreas[1], 70, 90, 0),
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
            ...makeAvailableTickets("evt-3", hamletAreas[0], 220, 260, 0),
            ...makeAvailableTickets("evt-3", hamletAreas[1], 320, 180, 0),
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
        // High demand event - lottery is the only way in.
        lotteryId: "lottery-evt-4",
        layout: { areas: aiCloudAreas },
        purchasePolicy: {
            minTicketsPerPurchase: 1,
            maxTicketsPerPurchase: 2,
            allowLoneSeat: true,
        },
        discountPolicy: { rules: [] },
        tickets: [
            ...makeAvailableTickets("evt-4", aiCloudAreas[0], 400, 850, 0),
            ...makeAvailableTickets("evt-4", aiCloudAreas[1], 1200, 350, 0),
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
        // Sold-out event - drives the queue CTA.
        purchasePolicy: {
            minTicketsPerPurchase: 1,
            maxTicketsPerPurchase: 4,
            allowLoneSeat: true,
        },
        discountPolicy: { rules: [] },
        tickets: [
            ...makeAvailableTickets(
                "evt-5",
                jazzRooftopAreas[0],
                0,
                240,
                0,
            ),
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
        tickets: [...makeAvailableTickets("evt-7", meetupAreas[0], 80, 50, 0)],
        description:
            "A community meetup focused on the latest in the React " +
            "ecosystem. Pizza included.",
    },
];

function matchesText(event: Event, rawText: string): boolean {
    const needle = rawText.trim().toLowerCase();
    if (!needle) {
        return true;
    }

    const haystack = [
        event.name,
        event.artist,
        event.type,
        event.location,
        ...event.tags,
    ]
        .join(" ")
        .toLowerCase();

    return haystack.includes(needle);
}

function matchesPriceRange(
    event: Event,
    priceMin: string,
    priceMax: string,
): boolean {
    const min = priceMin === "" ? null : Number(priceMin);
    const max = priceMax === "" ? null : Number(priceMax);
    const range = getPriceRange(event);

    if (min !== null && !Number.isNaN(min) && range.max < min) {
        return false;
    }
    if (max !== null && !Number.isNaN(max) && range.min > max) {
        return false;
    }
    return true;
}

function matchesDateRange(
    event: Event,
    dateFrom: string,
    dateTo: string,
): boolean {
    const eventTime = new Date(event.date).getTime();
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
        const toTime = new Date(`${dateTo}T23:59:59`).getTime();
        if (!Number.isNaN(toTime) && eventTime > toTime) {
            return false;
        }
    }
    return true;
}

function matchesSubstring(value: string, filter: string): boolean {
    const normalised = filter.trim().toLowerCase();
    if (!normalised) {
        return true;
    }
    return value.toLowerCase().includes(normalised);
}

// Mirrors Event.matches(EventSearchCriteria, ...) on the backend, plus the
// "publicly visible" gate (status must be ACTIVE for catalog search).
export function searchEvents(
    events: Event[],
    filters: EventSearchFilters,
): Event[] {
    const minEventRating =
        filters.minEventRating === "" ? null : Number(filters.minEventRating);
    const minCompanyRating =
        filters.minCompanyRating === ""
            ? null
            : Number(filters.minCompanyRating);

    return events.filter((event) => {
        if (event.status !== "ACTIVE") {
            return false;
        }
        if (filters.companyId && event.companyId !== filters.companyId) {
            return false;
        }
        if (!matchesText(event, filters.text)) {
            return false;
        }
        if (!matchesSubstring(event.location, filters.location)) {
            return false;
        }
        if (!matchesPriceRange(event, filters.priceMin, filters.priceMax)) {
            return false;
        }
        if (!matchesDateRange(event, filters.dateFrom, filters.dateTo)) {
            return false;
        }
        if (
            minEventRating !== null &&
            !Number.isNaN(minEventRating) &&
            event.rating < minEventRating
        ) {
            return false;
        }
        if (
            minCompanyRating !== null &&
            !Number.isNaN(minCompanyRating) &&
            event.companyRating < minCompanyRating
        ) {
            return false;
        }
        return true;
    });
}

// TODO: Replace the in-memory mock with the real communication layer once
// the protocol/API is implemented (UC 2.3.1 / UC 2.3.2).
export async function getEvents(): Promise<Event[]> {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve(mockEvents);
        }, 400);
    });
}

// TODO: Replace with a real GET /events/{id} call when the API exists.
// The Event Details screen (Issue #100) reads through this entrypoint, so
// it is the single place we will need to swap for the real implementation.
export async function getEventById(eventId: string): Promise<Event | null> {
    return new Promise((resolve) => {
        setTimeout(() => {
            const event = mockEvents.find((candidate) => candidate.id === eventId);
            resolve(event ?? null);
        }, 300);
    });
}
