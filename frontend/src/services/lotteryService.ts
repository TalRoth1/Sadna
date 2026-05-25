import axios from "axios";

type ApiResponse<T> = {
    success: boolean;
    message: string;
    data: T;
};

export async function registerToLottery(
    eventId: string,
    memberId: string,
    ticketAmount: number,
): Promise<void> {
    const response = await axios.post<ApiResponse<null>>(
        `/api/purchases/events/${eventId}/lottery/register`,
        {
            memberId,
            ticketAmount,
        },
    );

    if (!response.data.success) {
        throw new Error(response.data.message);
    }
}

export type DrawWinnersResponse = Record<string, string>;

export async function drawLotteryWinners(eventId: string, codeExpiryIso: string): Promise<DrawWinnersResponse> {
    const response = await fetch(`/api/purchases/events/${encodeURIComponent(eventId)}/lottery/draw`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ codeExpiry: codeExpiryIso }),
    });

    const body = await response.json();
    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to draw lottery winners.");
    }

    return body.data as DrawWinnersResponse;
}

export type CreateLotteryRequest = {
    registrationOpen: string;
    registrationClose: string;
};

export async function createLotteryForEvent(
    eventId: string,
    request: CreateLotteryRequest,
) {
    const response = await fetch(`/api/events/${eventId}/lottery`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    const body = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to create lottery for event.");
    }

    return body.data;
}