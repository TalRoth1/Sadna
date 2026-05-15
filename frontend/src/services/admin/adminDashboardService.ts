import type { AdminAction } from "../../types/admin";
import { verifyPlatformAdmin } from "./adminAuthService";

const mockAdminActions: AdminAction[] = [
    {
        id: "companies",
        title: "Close Production Company",
        description: "Close a production company and notify its owners and managers.",
    },
    {
        id: "subscribers",
        title: "Remove Subscriber",
        description:
            "Remove a registered subscriber and cancel all related roles and permissions.",
    },
    {
        id: "complaints",
        title: "Handle Complaints",
        description:
            "View subscriber complaints, respond to them, and send system messages.",
    },
    {
        id: "purchases",
        title: "Global Purchase History",
        description:
            "View system-wide purchase history by buyers, companies, and events.",
    },
    {
        id: "analytics",
        title: "System Analytics",
        description:
            "View live and historical system behavior and performance metrics.",
    },
    {
        id: "queues",
        title: "Queue Monitoring and Control",
        description:
            "View active queues and manually control user flow rate or clear queues.",
    },
];

// TODO: Replace this mock implementation with a real communication call.
// The server must return admin actions only if the provided userId is a platform admin.
export async function getAdminActions(userId: string): Promise<AdminAction[]> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    return new Promise((resolve) => {
        setTimeout(() => {
            resolve(mockAdminActions);
        }, 250);
    });
}