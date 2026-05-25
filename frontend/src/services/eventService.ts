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