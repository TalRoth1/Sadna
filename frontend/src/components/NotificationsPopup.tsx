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

function getNotificationPresentation(type?: string) {
    switch (type) {
        case "PURCHASE_COMPLETED":
            return {
                icon: "✓",
                label: "Purchase",
                className: "notification-card--success",
            };

        case "ACTIVE_PURCHASE_EXPIRING":
            return {
                icon: "⏳",
                label: "Expiring soon",
                className: "notification-card--warning",
            };

        case "COMPANY_CLOSED":
        case "EVENT_CANCELLED":
            return {
                icon: "!",
                label: "Important",
                className: "notification-card--danger",
            };

        case "QUEUE_ACCESS_GRANTED":
            return {
                icon: "→",
                label: "Queue",
                className: "notification-card--info",
            };

        case "ROLE_CHANGED":
            return {
                icon: "♢",
                label: "Role update",
                className: "notification-card--purple",
            };

        case "LOTTERY_WON":
            return {
                icon: "★",
                label: "Lottery",
                className: "notification-card--gold",
            };

        default:
            return {
                icon: "i",
                label: "Notification",
                className: "notification-card--default",
            };
    }
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

        if (!currentUser) {
            setNotifications([]);
            setErrorMessage("");
            return;
        }

        const userId = currentUser.id;

        disconnect = connectNotificationStream(
            userId,
            (notification) => {
                if (isCancelled) {
                    return;
                }

                setNotifications((currentNotifications) =>
                    mergeNotification(currentNotifications, notification),
                );

                alert(notification.message);
            },
        );

        async function loadUnreadNotifications() {
            try {
                setIsLoading(true);
                setErrorMessage("");

                const unreadNotifications = await getUnreadNotifications(userId);

                if (!isCancelled) {
                    setNotifications((currentNotifications) => {
                        let merged = currentNotifications;

                        for (const notification of unreadNotifications.filter(
                            (notification) => !notification.isRead,
                        )) {
                            merged = mergeNotification(merged, notification);
                        }

                        return merged;
                    });
                }
            } catch {
                if (!isCancelled) {
                    setErrorMessage("Failed to load previous notifications.");
                }
            } finally {
                if (!isCancelled) {
                    setIsLoading(false);
                }
            }
        }

        void loadUnreadNotifications();

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
                                    {notifications.map((notification) => {
                                        const presentation = getNotificationPresentation(notification.type);

                                        return (
                                            <article
                                                key={notification.id}
                                                className={`notification-card ${presentation.className}`}
                                            >
                                                <div className="notification-icon" aria-hidden="true">
                                                    {presentation.icon}
                                                </div>

                                                <div className="notification-card-content">
                                                    <div className="notification-card-topline">
                                                        <span className="notification-type-pill">
                                                            {presentation.label}
                                                        </span>

                                                        <small>
                                                            {new Date(notification.createdAt).toLocaleString()}
                                                        </small>
                                                    </div>

                                                    <strong>
                                                        {notification.title || "New notification"}
                                                    </strong>

                                                    <p>{notification.message}</p>

                                                    <div className="notification-actions">
                                                        {notification.targetUrl && (
                                                            <button
                                                                type="button"
                                                                className="notification-open-button"
                                                                onClick={() => {
                                                                    window.location.href = notification.targetUrl!;
                                                                }}
                                                            >
                                                                Open
                                                            </button>
                                                        )}

                                                        <button
                                                            type="button"
                                                            className="notification-read-button"
                                                            onClick={() =>
                                                                handleMarkAsRead(notification.id)
                                                            }
                                                        >
                                                            Mark as read
                                                        </button>
                                                    </div>
                                                </div>
                                            </article>
                                        );
                                    })}
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