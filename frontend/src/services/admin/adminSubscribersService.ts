import type { Subscriber } from "../../types/admin";
import { verifyPlatformAdmin } from "./adminAuthService";

const mockSubscribers: Subscriber[] = [
    {
        id: "subscriber-1",
        username: "daniel",
        email: "daniel@example.com",
        status: "active",
    },
    {
        id: "subscriber-2",
        username: "maya",
        email: "maya@example.com",
        status: "active",
    },
];

// TODO: Replace this mock implementation with a real server call.
// The server must verify admin permissions before returning platform subscribers.
export async function getSubscribers(userId: string): Promise<Subscriber[]> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    return mockSubscribers;
}

// TODO: Replace this mock implementation with a real server command.
// The server must verify admin permissions, remove the subscriber,
// and cancel all roles/permissions across production companies.
export async function removeSubscriber(
    userId: string,
    subscriberId: string,
): Promise<void> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    console.log("Remove subscriber request:", { userId, subscriberId });
}