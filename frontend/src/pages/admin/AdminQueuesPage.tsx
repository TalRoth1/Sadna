import { useEffect, useState } from "react";
import { getCurrentUser } from "../../services/currentUserService";
import {
    clearQueue,
    getActiveQueues,
    updateQueueFlowRate,
} from "../../services/admin/adminQueuesService";
import type { QueueInfo } from "../../types/admin";

export default function AdminQueuesPage() {
    const [queues, setQueues] = useState<QueueInfo[]>([]);
    const [selectedQueueId, setSelectedQueueId] = useState("");
    const [flowRate, setFlowRate] = useState(0);
    const [adminUserId, setAdminUserId] = useState("");
    const [message, setMessage] = useState("");

    useEffect(() => {
        async function loadQueues() {
            const currentUser = await getCurrentUser();
            const result = await getActiveQueues(currentUser.id);
            const firstQueue = result[0];

            setAdminUserId(currentUser.id);
            setQueues(result);
            setSelectedQueueId(firstQueue?.id ?? "");
            setFlowRate(firstQueue?.flowRatePerMinute ?? 0);
        }

        loadQueues();
    }, []);

    function handleQueueChange(queueId: string) {
        const selectedQueue = queues.find((queue) => queue.id === queueId);

        setSelectedQueueId(queueId);
        setFlowRate(selectedQueue?.flowRatePerMinute ?? 0);
    }

    async function handleUpdateFlowRate() {
        if (!selectedQueueId || flowRate <= 0) {
            return;
        }

        await updateQueueFlowRate(adminUserId, selectedQueueId, flowRate);
        setMessage("Queue flow rate update request was submitted.");
    }

    async function handleClearQueue() {
        if (!selectedQueueId) {
            return;
        }

        await clearQueue(adminUserId, selectedQueueId);
        setMessage("Queue clear request was submitted.");
    }

    return (
        <main className="admin-page">
            <section className="page-header">
                <h1>Queue Monitoring and Control</h1>
                <p>View active queues and control user flow rate.</p>
            </section>

            <section className="admin-panel">
                <select
                    value={selectedQueueId}
                    onChange={(event) => handleQueueChange(event.target.value)}
                >
                    {queues.map((queue) => (
                        <option key={queue.id} value={queue.id}>
                            {queue.eventName} - {queue.waitingUsers} waiting users
                        </option>
                    ))}
                </select>

                <input
                    type="number"
                    min="1"
                    value={flowRate}
                    onChange={(event) => setFlowRate(Number(event.target.value))}
                />

                <button type="button" onClick={handleUpdateFlowRate}>
                    Update Flow Rate
                </button>

                <button type="button" onClick={handleClearQueue}>
                    Clear Queue
                </button>

                {message && <p className="success-message">{message}</p>}
            </section>
        </main>
    );
}