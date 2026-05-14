import type { QueueInfo } from "../../types/admin";
import { verifyPlatformAdmin } from "./adminAuthService";

const mockQueues: QueueInfo[] = [
    {
        id: "queue-1",
        eventName: "Summer Music Festival",
        waitingUsers: 320,
        flowRatePerMinute: 40,
        status: "active",
    },
    {
        id: "queue-2",
        eventName: "Big Arena Show",
        waitingUsers: 780,
        flowRatePerMinute: 60,
        status: "active",
    },
];

// TODO: Replace this mock implementation with a real server call.
// The server must verify admin permissions and return all active queues in the platform.
export async function getActiveQueues(userId: string): Promise<QueueInfo[]> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    return mockQueues;
}

// TODO: Replace this mock implementation with a real server command.
// The server must verify admin permissions and update the selected queue flow rate.
export async function updateQueueFlowRate(
    userId: string,
    queueId: string,
    flowRatePerMinute: number,
): Promise<void> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    console.log("Update queue flow rate request:", {
        userId,
        queueId,
        flowRatePerMinute,
    });
}

// TODO: Replace this mock implementation with a real server command.
// The server must verify admin permissions and clear the selected queue in case of technical issues.
export async function clearQueue(
    userId: string,
    queueId: string,
): Promise<void> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    console.log("Clear queue request:", { userId, queueId });
}