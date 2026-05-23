import api from "../api";
import type { Subscriber } from "../../types/admin";

const mockSubscribers: Subscriber[] = [
    {
        id: "daniel@example.com",
        username: "daniel",
        email: "daniel@example.com",
        status: "active",
    },
];

export async function getSubscribers(_userId: string): Promise<Subscriber[]> {
    return mockSubscribers;
}

export async function removeSubscriber(
    _userId: string,
    subscriberId: string,
): Promise<void> {
    await api.delete(`/admin/subscribers/${encodeURIComponent(subscriberId)}`);
}