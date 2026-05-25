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
};

export type EditEventRequest = {
    name: string;
    date: string;
    location: string;
    artist: string;
    type: string;
    status: string;
    description?: string;
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