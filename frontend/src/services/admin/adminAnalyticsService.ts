import type { SystemAnalytics } from "../../types/admin";
import { verifyPlatformAdmin } from "./adminAuthService";

const mockAnalytics: SystemAnalytics = {
    activeVisitors: 128,
    newSubscribersRate: 14,
    ticketReservationRate: 42,
    ticketPurchaseRate: 31,
    activeQueues: 3,
};

// TODO: Replace this mock implementation with a real server call.
// The server must verify admin permissions and return live/historical system analytics.
export async function getSystemAnalytics(
    userId: string,
): Promise<SystemAnalytics> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    return mockAnalytics;
}