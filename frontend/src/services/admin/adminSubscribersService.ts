import api from "../api";
import type { Subscriber } from "../../types/admin";

type AdminSubscriberDto = {
    id: string;
    username: string;
    email: string;
    status: string;
    role: string;
};

function mapStatus(status: string): Subscriber["status"] {
    const normalizedStatus = status.toLowerCase();

    if (
        normalizedStatus === "removed" ||
        normalizedStatus === "deleted" ||
        normalizedStatus === "disabled"
    ) {
        return "removed";
    }

    return "active";
}

function mapSubscriber(subscriber: AdminSubscriberDto): Subscriber {
    return {
        id: subscriber.username,
        username: subscriber.username,
        email: subscriber.email,
        status: mapStatus(subscriber.status),
    };
}

export async function getSubscribers(_userId: string): Promise<Subscriber[]> {
    const response = await api.get("/admin/subscribers");
    const subscribers = response.data.data as AdminSubscriberDto[];

    return subscribers
        .map(mapSubscriber)
        .filter((subscriber) => subscriber.status === "active");
}

export async function removeSubscriber(
    _userId: string,
    subscriberUsername: string,
): Promise<void> {
    await api.delete(`/admin/subscribers/${encodeURIComponent(subscriberUsername)}`);
}