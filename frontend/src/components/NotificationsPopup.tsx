import { useEffect, useState } from "react";
import {
    getUnreadNotifications,
    markAllNotificationsAsRead,
    markNotificationAsRead,
} from "../services/notificationService";
import type { CurrentUser } from "../services/currentUserService";
import type { UserNotification } from "../types/notification";

type NotificationsPopupProps = {
    currentUser: CurrentUser | null;
};

function formatNotificationDate(date: string) {
    const parsedDate = new Date(date);

    if (Number.isNaN(parsedDate.getTime())) {
        return "Unknown date";
    }

    return parsedDate.toLocaleString("he-IL", {
        dateStyle: "short",
        timeStyle: "short",
    });
}

export default function NotificationsPopup({
                                               currentUser,
                                           }: NotificationsPopupProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [notifications, setNotifications] = useState<UserNotification[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        async function loadNotificationsAfterLogin() {
            if (!currentUser) {
                setNotifications([]);
                setIsOpen(false);
                return;
            }

            try {
                setIsLoading(true);
                setErrorMessage("");

                const unreadNotifications = await getUnreadNotifications(currentUser.id);
                const unreadOnly = unreadNotifications.filter(
                    (notification) => !notification.isRead,
                );

                setNotifications(unreadOnly);

                if (unreadOnly.length > 0) {
                    setIsOpen(true);
                }
            } catch {
                setErrorMessage("Failed to load notifications.");
            } finally {
                setIsLoading(false);
            }
        }

        loadNotificationsAfterLogin();
    }, [currentUser?.id]);

    async function handleMarkAsRead(notificationId: string) {
        if (!currentUser) {
            return;
        }

        await markNotificationAsRead(currentUser.id, notificationId);

        setNotifications((currentNotifications) =>
            currentNotifications.filter(
                (notification) => notification.id !== notificationId,
            ),
        );
    }

    async function handleMarkAllAsRead() {
        if (!currentUser) {
            return;
        }

        await markAllNotificationsAsRead(currentUser.id);
        setNotifications([]);
    }

    if (!currentUser) {
        return null;
    }

    return (
        <div className="notifications-wrapper">
            <button
                type="button"
                className="notifications-button"
                onClick={() => setIsOpen((currentValue) => !currentValue)}
                aria-label="Open notifications"
            >
                🔔
                {notifications.length > 0 && (
                    <span className="notifications-badge">{notifications.length}</span>
                )}
            </button>

            {isOpen && (
                <section className="notifications-popup">
                    <div className="notifications-header">
                        <div>
                            <h2>Notifications</h2>
                            <p>Unread notifications</p>
                        </div>

                        <button
                            type="button"
                            className="notifications-close-button"
                            onClick={() => setIsOpen(false)}
                            aria-label="Close notifications"
                        >
                            ×
                        </button>
                    </div>

                    {isLoading && (
                        <div className="notifications-empty-state">
                            Loading notifications...
                        </div>
                    )}

                    {!isLoading && errorMessage && (
                        <div className="notifications-empty-state">{errorMessage}</div>
                    )}

                    {!isLoading && !errorMessage && notifications.length === 0 && (
                        <div className="notifications-empty-state">
                            No unread notifications.
                        </div>
                    )}

                    {!isLoading && !errorMessage && notifications.length > 0 && (
                        <>
                            <div className="notifications-list">
                                {notifications.map((notification) => (
                                    <article key={notification.id} className="notification-item">
                                        <div>
                                            <h3>{notification.title}</h3>
                                            <p>{notification.message}</p>
                                            <span>{formatNotificationDate(notification.createdAt)}</span>
                                        </div>

                                        <button
                                            type="button"
                                            onClick={() => handleMarkAsRead(notification.id)}
                                        >
                                            Mark as read
                                        </button>
                                    </article>
                                ))}
                            </div>

                            <button
                                type="button"
                                className="mark-all-read-button"
                                onClick={handleMarkAllAsRead}
                            >
                                Mark all as read
                            </button>
                        </>
                    )}
                </section>
            )}
        </div>
    );
}