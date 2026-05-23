import { useNotifications } from "../context/NotificationProvider";

export function NotificationsPage() {
    const { notifications, markRead, markAllRead } = useNotifications();

    return (
        <main>
            <h1>Notifications</h1>

            <button onClick={markAllRead}>
                Mark all as read
            </button>

            {notifications.length === 0 && <p>No notifications yet.</p>}

            {notifications.map((notification) => (
                <article
                    key={notification.id}
                    style={{
                        border: "1px solid #ddd",
                        padding: 12,
                        marginTop: 12,
                        opacity: notification.isRead ? 0.6 : 1,
                    }}
                >
                    <h3>{notification.title}</h3>
                    <p>{notification.message}</p>
                    <small>{new Date(notification.createdAt).toLocaleString()}</small>

                    <div>
                        {!notification.isRead && (
                            <button onClick={() => markRead(notification.id)}>
                                Mark as read
                            </button>
                        )}

                        {notification.targetUrl && (
                            <button
                                onClick={() => {
                                    window.location.href = notification.targetUrl!;
                                }}
                            >
                                Open
                            </button>
                        )}
                    </div>
                </article>
            ))}
        </main>
    );
}