import type { GlobalPurchaseRecord } from "../../types/admin";
import { verifyPlatformAdmin } from "./adminAuthService";

const mockGlobalPurchases: GlobalPurchaseRecord[] = [
    {
        id: "purchase-1",
        buyerName: "Ofek",
        companyName: "Live Nation Israel",
        eventName: "Summer Music Festival",
        ticketsAmount: 2,
        totalPrice: 240,
        purchaseDate: "2026-05-10T18:20:00",
    },
    {
        id: "purchase-2",
        buyerName: "Maya",
        companyName: "Urban Events",
        eventName: "Standup Night",
        ticketsAmount: 1,
        totalPrice: 90,
        purchaseDate: "2026-04-01T12:15:00",
    },
];

// TODO: Replace this mock implementation with a real server call.
// The server must verify admin permissions and return global purchase history,
// including filtering by buyer, company, or event when supported.
export async function getGlobalPurchaseHistory(
    userId: string,
): Promise<GlobalPurchaseRecord[]> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    return mockGlobalPurchases;
}