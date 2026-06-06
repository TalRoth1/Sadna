import api from "../api";
import type { AdminAction } from "../../types/admin";

const adminActions: AdminAction[] = [
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

export async function getAdminActions(_userId: string): Promise<AdminAction[]> {
    return adminActions;
}