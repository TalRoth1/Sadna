import { useEffect, useState } from "react";
import {
    connectNotificationStream,
    getUnreadNotifications,
    markAllNotificationsAsRead,
    markNotificationAsRead,
} from "../services/notificationService";
import type { CurrentUser } from "../services/currentUserService";
import type { UserNotification } from "../types/notification";

type NotificationsPopupProps = {
    currentUser: CurrentUser | null;
};

function mergeNotification(
    currentNotifications: UserNotification[],
    incoming: UserNotification,
): UserNotification[] {
    const alreadyExists = currentNotifications.some(
        (notification) => notification.id === incoming.id,
    );

    if (alreadyExists) {
        return currentNotifications.map((notification) =>
            notification.id === incoming.id ? incoming : notification,
        );
    }

    return [incoming, ...currentNotifications];
}

export default function NotificationsPopup({
    currentUser,
}: NotificationsPopupProps) {
    const [isOpen, setIsOpen] = useState(false);
    const [notifications, setNotifications] = useState<UserNotification[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        let disconnect: (() => void) | undefined;
        let isCancelled = false;

        async function initNotifications() {
            if (!currentUser) {
                setNotifications([]);
                setErrorMessage("");
                return;
            }

            try {
                setIsLoading(true);
                setErrorMessage("");

                const unreadNotifications = await getUnreadNotifications(
                    currentUser.id,
                );

                if (!isCancelled) {
                    setNotifications(
                        unreadNotifications.filter(
                            (notification) => !notification.isRead,
                        ),
                    );
                }

                disconnect = connectNotificationStream(
                    currentUser.id,
                    (notification) => {
                        setNotifications((currentNotifications) =>
                            mergeNotification(
                                currentNotifications,
                                notification,
                            ),
                        );

                        // Temporary in-app feedback.
                        // Later we can replace this with a styled toast.
                        console.log("New notification:", notification.message);
                    },
                );
            } catch {
                if (!isCancelled) {
                    setErrorMessage("Failed to load notifications.");
                }
            } finally {
                if (!isCancelled) {
                    setIsLoading(false);
                }
            }
        }

        initNotifications();

        return () => {
            isCancelled = true;
            disconnect?.();
        };
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

    const unreadCount = notifications.filter(
        (notification) => !notification.isRead,
    ).length;

    return (
        <div className="notifications-wrapper">
            <button
                type="button"
                className="notifications-button"
                onClick={() => setIsOpen((current) => !current)}
                aria-label="Open notifications"
            >
                🔔
                {unreadCount > 0 && (
                    <span className="notifications-badge">{unreadCount}</span>
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
                        <div className="notifications-empty-state">
                            {errorMessage}
                        </div>
                    )}

                    {!isLoading &&
                        !errorMessage &&
                        notifications.length === 0 && (
                            <div className="notifications-empty-state">
                                No unread notifications.
                            </div>
                        )}

                    {!isLoading &&
                        !errorMessage &&
                        notifications.length > 0 && (
                            <>
                                <div className="notifications-list">
                                    {notifications.map((notification) => (
                                        <article
                                            key={notification.id}
                                            className="notification-card"
                                        >
                                            <div className="notification-card-content">
                                                <strong>
                                                    {notification.title}
                                                </strong>
                                                <p>{notification.message}</p>
                                                <small>
                                                    {new Date(
                                                        notification.createdAt,
                                                    ).toLocaleString()}
                                                </small>
                                            </div>

                                            <button
                                                type="button"
                                                className="notification-read-button"
                                                onClick={() =>
                                                    handleMarkAsRead(
                                                        notification.id,
                                                    )
                                                }
                                            >
                                                Mark as read
                                            </button>
                                        </article>
                                    ))}
                                </div>

                                <button
                                    type="button"
                                    className="notifications-mark-all-button"
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