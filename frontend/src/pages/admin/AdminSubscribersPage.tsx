import { useEffect, useState } from "react";
import { getCurrentUser } from "../../services/currentUserService";
import {
    getSubscribers,
    removeSubscriber,
} from "../../services/admin/adminSubscribersService";
import type { Subscriber } from "../../types/admin";

export default function AdminSubscribersPage() {
    const [subscribers, setSubscribers] = useState<Subscriber[]>([]);
    const [selectedSubscriberId, setSelectedSubscriberId] = useState("");
    const [adminUserId, setAdminUserId] = useState("");
    const [message, setMessage] = useState("");

    useEffect(() => {
        async function loadSubscribers() {
            const currentUser = await getCurrentUser();
            const result = await getSubscribers(currentUser.id);

            setAdminUserId(currentUser.id);
            setSubscribers(result);
            setSelectedSubscriberId(result[0]?.id ?? "");
        }

        loadSubscribers();
    }, []);

    async function handleRemoveSubscriber() {
        if (!selectedSubscriberId) {
            return;
        }

        await removeSubscriber(adminUserId, selectedSubscriberId);
        setMessage("Subscriber removal request was submitted.");
    }

    return (
        <main className="admin-page">
            <section className="page-header">
                <h1>Remove Subscriber</h1>
                <p>Remove a registered subscriber from the platform.</p>
            </section>

            <section className="admin-panel">
                <select
                    value={selectedSubscriberId}
                    onChange={(event) => setSelectedSubscriberId(event.target.value)}
                >
                    {subscribers.map((subscriber) => (
                        <option key={subscriber.id} value={subscriber.id}>
                            {subscriber.username} - {subscriber.email}
                        </option>
                    ))}
                </select>

                <button type="button" onClick={handleRemoveSubscriber}>
                    Remove Subscriber
                </button>

                {message && <p className="success-message">{message}</p>}
            </section>
        </main>
    );
}