import type { Complaint } from "../../types/admin";
import { verifyPlatformAdmin } from "./adminAuthService";

const mockComplaints: Complaint[] = [
    {
        id: "complaint-1",
        title: "Fake ticket report",
        message: "The user reported a suspicious ticket for an event.",
        reporterName: "Ofek",
        status: "open",
    },
    {
        id: "complaint-2",
        title: "Event was cancelled without notice",
        message: "A buyer claims they did not receive a cancellation notice.",
        reporterName: "Maya",
        status: "open",
    },
];

// TODO: Replace this mock implementation with a real server call.
// The server must verify admin permissions before returning complaints.
export async function getComplaints(userId: string): Promise<Complaint[]> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    return mockComplaints;
}

// TODO: Replace this mock implementation with a real server command.
// The server must verify admin permissions and store/send the admin response.
export async function respondToComplaint(
    userId: string,
    complaintId: string,
    response: string,
): Promise<void> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    console.log("Complaint response request:", {
        userId,
        complaintId,
        response,
    });
}

// TODO: Replace this mock implementation with a real server command.
// The server must verify admin permissions and send the system message to the selected audience.
export async function sendSystemMessage(
    userId: string,
    message: string,
): Promise<void> {
    const isAdmin = await verifyPlatformAdmin(userId);

    if (!isAdmin) {
        throw new Error("User is not a platform admin");
    }

    console.log("System message request:", { userId, message });
}