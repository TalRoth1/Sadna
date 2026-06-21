import type { EventDiscountType } from "./companyService";

type ApiResponse<T> = {
    success: boolean;
    message: string;
    data: T | null;
};

export type CreateEventRequest = {
    companyId: string;
    eventManagerEmail: string;
    name: string;
    date: string;
    location: string;
    artist: string;
    type: string;
    status: string;
    description?: string;
    discountType: EventDiscountType;
};

export type EditEventRequest = {
    name: string;
    date: string;
    location: string;
    artist: string;
    type: string;
    status: string;
    description?: string;
    requesterEmail: string;
};

export type AddEventPolicyRuleRequest = {
    username: string;
    companyId: string;
    age: number | null;
    minTicket: number | null;
    maxTicket: number | null;
    allowLoneSeat: boolean | null;
};

export type DeleteEventPolicyRuleRequest = {
    username: string;
    companyId: string;
};

export async function createEvent(request: CreateEventRequest) {
    const response = await fetch("/api/events", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to create event.");
    }

    return body.data;
}

export async function editEvent(eventId: string, request: EditEventRequest) {
    const response = await fetch(`/api/events/${eventId}`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to update event.");
    }

    return body.data;
}

export async function addEventPolicyRule(
    eventId: string,
    request: AddEventPolicyRuleRequest,
) {
    const response = await fetch(`/api/events/${eventId}/policy`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to add event policy rule.");
    }

    return body.data;
}

export async function deleteEventPolicyRule(
    eventId: string,
    ruleId: string,
    request: DeleteEventPolicyRuleRequest,
) {
    const response = await fetch(`/api/events/${eventId}/policy/${ruleId}`, {
        method: "DELETE",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to delete event policy rule.");
    }

    return body.data;
}

export type EditEventPolicyRequest = {
    username: string;
    companyId: string;
    age: number | null;
    minTicket: number | null;
    maxTicket: number | null;
    allowLoneSeat: boolean | null;
};

export async function editEventPolicy(
    eventId: string,
    request: EditEventPolicyRequest,
) {
    const response = await fetch(`/api/events/${eventId}/policy`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to update event policy.");
    }

    return body.data;
}

export type AddStandingAreaRequest = {
    price: number;
    count: number;
};

export type AddSittingAreaRequest = {
    price: number;
    rows: number;
    seatsPerRow: number;
};

export type UpdateStandingAreaRequest = {
    username: string;
    companyId: string;
    price: number;
    count: number;
};

export type UpdateSittingAreaRequest = {
    username: string;
    companyId: string;
    price: number;
    rows: number;
    seatsPerRow: number;
};

export type DeleteAreaRequest = {
    username: string;
    companyId: string;
};

export type AddOvertDiscountRequest = {
    username: string;
    companyId: string;
    fromDate: string | null;
    toDate: string;
    discountPercent: number;
};

export type AddCouponDiscountRequest = {
    username: string;
    companyId: string;
    fromDate: string | null;
    toDate: string;
    discountPercent: number;
    code: string;
};

export type AddConditionalDiscountRequest = {
    username: string;
    companyId: string;
    fromDate: string | null;
    toDate: string;
    discountPercent: number;
    requiredTickets: number;
    appliedTickets: number;
};

export type RemoveEventDiscountRequest = {
    username: string;
    companyId: string;
};

export async function addStandingArea(
    eventId: string,
    request: AddStandingAreaRequest,
) {
    const response = await fetch(`/api/events/${eventId}/areas/standing`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to create standing area.");
    }

    return body.data;
}

export async function addSittingArea(
    eventId: string,
    request: AddSittingAreaRequest,
) {
    const response = await fetch(`/api/events/${eventId}/areas/sitting`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to create sitting area.");
    }

    return body.data;
}

export async function updateStandingArea(
    eventId: string,
    areaId: string,
    request: UpdateStandingAreaRequest,
) {
    const response = await fetch(
        `/api/events/${eventId}/areas/${areaId}/standing`,
        {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to update standing area.");
    }

    return body.data;
}

export async function updateSittingArea(
    eventId: string,
    areaId: string,
    request: UpdateSittingAreaRequest,
) {
    const response = await fetch(
        `/api/events/${eventId}/areas/${areaId}/sitting`,
        {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to update sitting area.");
    }

    return body.data;
}

export async function deleteEventArea(
    eventId: string,
    areaId: string,
    request: DeleteAreaRequest,
) {
    const response = await fetch(`/api/events/${eventId}/areas/${areaId}`, {
        method: "DELETE",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to delete ticket area.");
    }

    return body.data;
}

export async function addOvertDiscount(
    eventId: string,
    request: AddOvertDiscountRequest,
) {
    const response = await fetch(`/api/events/${eventId}/discounts/overt`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to add overt discount.");
    }

    return body.data;
}

export async function addCouponDiscount(
    eventId: string,
    request: AddCouponDiscountRequest,
) {
    const response = await fetch(`/api/events/${eventId}/discounts/coupon`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to add coupon discount.");
    }

    return body.data;
}

export async function addConditionalDiscount(
    eventId: string,
    request: AddConditionalDiscountRequest,
) {
    const response = await fetch(
        `/api/events/${eventId}/discounts/conditional`,
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to add conditional discount.");
    }

    return body.data;
}

export async function removeEventDiscount(
    eventId: string,
    discountId: string,
    request: RemoveEventDiscountRequest,
) {
    const response = await fetch(
        `/api/events/${eventId}/discounts/${discountId}`,
        {
            method: "DELETE",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    const body: ApiResponse<unknown> = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to remove event discount.");
    }

    return body.data;
}