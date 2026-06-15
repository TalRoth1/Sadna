import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import type { UserNotification } from "../src/types/notification";
import { getCurrentUser } from "../src/services/currentUserService";
import {
    connectNotificationStream,
    getUnreadNotifications,
    markAllNotificationsAsRead,
    markNotificationAsRead,
} from "../src/services/notificationService";

type NotificationContextValue = {
    notifications: UserNotification[];
    unreadCount: number;
    markRead: (notificationId: string) => Promise<void>;
    markAllRead: () => Promise<void>;
};

const NotificationContext = createContext<NotificationContextValue | null>(null);

export function NotificationProvider({ children }: { children: ReactNode }) {
    const [userId, setUserId] = useState<string | null>(null);
    const [notifications, setNotifications] = useState<UserNotification[]>([]);

    useEffect(() => {
        getCurrentUser().then((user) => {
            setUserId(user?.id ?? null);
        });
    }, []);

    useEffect(() => {
        if (!userId) {
            setNotifications([]);
            return;
        }

        let disconnect: (() => void) | undefined;

        getUnreadNotifications(userId).then(setNotifications);

        disconnect = connectNotificationStream(
            userId,
            (notification) => {
                setNotifications((prev) => [notification, ...prev]);

                if (notification.type === "QUEUE_ACCESS_GRANTED") {
                    alert(notification.message);

                    if (notification.targetUrl) {
                        window.location.href = notification.targetUrl;
                    }

                    return;
                }

                alert(notification.message);
            },
            () => console.log("Connected to notifications stream"),
            () => console.warn("Notifications stream disconnected"),
        );

        return () => {
            disconnect?.();
        };
    }, [userId]);

    const unreadCount = notifications.filter((n) => !n.isRead).length;

    async function markRead(notificationId: string) {
        if (!userId) return;

        await markNotificationAsRead(userId, notificationId);

        setNotifications((prev) =>
            prev.map((notification) =>
                notification.id === notificationId
                    ? { ...notification, isRead: true }
                    : notification,
            ),
        );
    }

    async function markAllRead() {
        if (!userId) return;

        await markAllNotificationsAsRead(userId);

        setNotifications((prev) =>
            prev.map((notification) => ({
                ...notification,
                isRead: true,
            })),
        );
    }

    const value = useMemo(
        () => ({
            notifications,
            unreadCount,
            markRead,
            markAllRead,
        }),
        [notifications, unreadCount],
    );

    return (
        <NotificationContext.Provider value={value}>
            {children}
        </NotificationContext.Provider>
    );
}

export function useNotifications() {
    const context = useContext(NotificationContext);

    if (!context) {
        throw new Error("useNotifications must be used inside NotificationProvider");
    }

    return context;
}