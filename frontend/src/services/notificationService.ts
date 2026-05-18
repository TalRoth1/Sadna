import type { UserNotification } from "../types/notification";

const mockUnreadNotificationsByUserId: Record<string, UserNotification[]> = {
    "user-1": [
        {
            id: "notification-1",
            title: "Purchase completed",
            message: "Your purchase for Summer Music Festival was completed successfully.",
            createdAt: "2026-05-14T20:15:00",
            isRead: false,
        },
        {
            id: "notification-2",
            title: "Queue update",
            message: "You moved forward in the queue for Big Arena Show.",
            createdAt: "2026-05-14T20:45:00",
            isRead: false,
        },
        {
            id: "notification-3",
            title: "Event reminder",
            message: "Standup Night starts soon. Please check your ticket details.",
            createdAt: "2026-05-14T21:10:00",
            isRead: false,
        },
    ],
};

// TODO: Replace this mock implementation with a real communication call once the protocol/API is implemented.
// The server should return unread notifications for the provided userId, including notifications received while the user was offline.
export async function getUnreadNotifications(
    userId: string,
): Promise<UserNotification[]> {
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve(mockUnreadNotificationsByUserId[userId] ?? []);
        }, 300);
    });
}

// TODO: Replace this mock implementation with a real server command once communication is implemented.
// The server should mark the selected notification as read for the provided userId.
export async function markNotificationAsRead(
    userId: string,
    notificationId: string,
): Promise<void> {
    const notifications = mockUnreadNotificationsByUserId[userId] ?? [];

    mockUnreadNotificationsByUserId[userId] = notifications.map((notification) =>
        notification.id === notificationId
            ? { ...notification, isRead: true }
            : notification,
    );
}

// TODO: Replace this mock implementation with a real server command once communication is implemented.
// The server should mark all unread notifications as read for the provided userId.
export async function markAllNotificationsAsRead(userId: string): Promise<void> {
    const notifications = mockUnreadNotificationsByUserId[userId] ?? [];

    mockUnreadNotificationsByUserId[userId] = notifications.map((notification) => ({
        ...notification,
        isRead: true,
    }));
}