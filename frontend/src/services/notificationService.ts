import api from "./api";
import type { UserNotification } from "../types/notification";

type NotificationDto = {
    id: string;
    recipientId: string;
    type?: string;
    message: string;
    targetUrl?: string | null;
    createdAt: string;
    read: boolean;
    readAt?: string | null;
};

const API_BASE_URL =
    import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

function titleFromMessage(message: string): string {
    if (message.includes("turn has arrived")) return "Your turn has arrived";
    if (message.includes("Purchase")) return "Purchase update";
    if (message.includes("changed")) return "Event updated";
    if (message.includes("SOLD OUT")) return "Sold out";
    return "Notification";
}

function mapDto(dto: NotificationDto): UserNotification {
    return {
        id: dto.id,
        title: titleFromMessage(dto.message),
        message: dto.message,
        createdAt: dto.createdAt,
        isRead: dto.read,
        type: dto.type as UserNotification["type"],
        targetUrl: dto.targetUrl ?? null,
    };
}

function fromStreamData(raw: string): UserNotification {
    try {
        const parsed = JSON.parse(raw);

        if (parsed.id && parsed.message) {
            return mapDto(parsed as NotificationDto);
        }

        if (parsed.notification) {
            return mapDto(parsed.notification as NotificationDto);
        }
    } catch {
        // In the current backend, live notifications may arrive as plain text.
    }

    return {
        id: crypto.randomUUID(),
        title: titleFromMessage(raw),
        message: raw,
        createdAt: new Date().toISOString(),
        isRead: false,
    };
}

export async function getUnreadNotifications(
    userId: string,
): Promise<UserNotification[]> {
    const response = await api.get(`/notifications/users/${userId}`, {
        params: { unreadOnly: true },
    });

    const rows = (response.data.data ?? []) as NotificationDto[];
    return rows.map(mapDto);
}

export async function markNotificationAsRead(
    userId: string,
    notificationId: string,
): Promise<void> {
    await api.patch(`/notifications/${notificationId}/read`, null, {
        params: { userId },
    });
}

export async function markAllNotificationsAsRead(userId: string): Promise<void> {
    await api.patch(`/notifications/users/${userId}/read-all`);
}

export function connectNotificationStream(
    userId: string,
    onNotification: (notification: UserNotification) => void,
    onConnected?: () => void,
    onDisconnected?: () => void,
): () => void {
    const source = new EventSource(
        `${API_BASE_URL}/notifications/stream/${userId}`,
    );

    source.addEventListener("notification", (event) => {
        onNotification(fromStreamData(event.data));
    });

    source.addEventListener("delayed-notification", (event) => {
        onNotification(fromStreamData(event.data));
    });

    source.addEventListener("connected", () => {
        if (onConnected) {
            onConnected();
        }
    });

    source.onerror = () => {
        if (onDisconnected) {
            onDisconnected();
        }
    };

    return () => source.close();
}