import type { PurchaseHistoryItem } from "../types/purchase";

const mockPurchases: PurchaseHistoryItem[] = [
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
];

// TODO: Replace mock data with the real communication layer once the protocol/API is implemented.
// Keep the UI using this service function instead of calling the server directly from the component.
export async function getPurchaseHistory(): Promise<PurchaseHistoryItem[]> {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve(mockPurchases);
        }, 400);
    });
}