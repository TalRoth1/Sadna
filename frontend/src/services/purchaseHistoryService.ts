import type { PurchaseHistoryItem } from "../types/purchase";

const mockPurchasesByUserId: Record<string, PurchaseHistoryItem[]> = {
    "user-1": [
        {
            id: "1",
            eventName: "Summer Music Festival",
            eventDate: "2026-06-20T20:30:00",
            eventLocation: "Tel Aviv Park",
            ticketsAmount: 2,
            totalPrice: 240,
        },
        {
            id: "2",
            eventName: "Standup Night",
            eventDate: "2026-04-07T21:00:00",
            eventLocation: "Haifa Theater",
            ticketsAmount: 1,
            totalPrice: 90,
        },
    ],
};

// TODO: Replace mock data with the real communication layer once the protocol/API is implemented.
// This function should request purchase history for the current authenticated user only.
export async function getPurchaseHistory(
    userId: string,
): Promise<PurchaseHistoryItem[]> {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve(mockPurchasesByUserId[userId] ?? []);
        }, 400);
    });
}