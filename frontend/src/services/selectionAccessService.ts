import api from "./api";

export type SelectionAccess = {
    eventId: string;
    userId: string;
    allowed: boolean;
    positionInQueue: number;
    queueSize: number;
    accessExpiresAt: string | null;
    message: string;
};

function unwrapSelectionAccess(responseData: unknown): SelectionAccess {
    const wrapped = responseData as { data?: SelectionAccess; message?: string };

    if (!wrapped.data) {
        throw new Error(wrapped.message || "Selection access response was empty.");
    }

    return wrapped.data;
}

export async function requestSelectionAccess(
    eventId: string,
    userId: string,
): Promise<SelectionAccess> {
    const response = await api.post(
        `/purchases/events/${encodeURIComponent(eventId)}/selection-access`,
        { userId },
    );

    return unwrapSelectionAccess(response.data);
}

export async function getSelectionAccessStatus(
    eventId: string,
    userId: string,
): Promise<SelectionAccess> {
    const response = await api.get(
        `/purchases/events/${encodeURIComponent(eventId)}/selection-access`,
        { params: { userId } },
    );

    return unwrapSelectionAccess(response.data);
}
