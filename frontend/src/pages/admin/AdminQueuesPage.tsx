import { useEffect, useState } from "react";
import { getCurrentUser } from "../../services/currentUserService";
import {
    clearQueue,
    getActiveQueues,
    releaseQueueBatch,
    updateQueueFlowRate,
} from "../../services/admin/adminQueuesService";
import type { QueueInfo } from "../../types/admin";

function getErrorMessage(error: unknown, fallback: string): string {
    return error instanceof Error ? error.message : fallback;
}

export default function AdminQueuesPage() {
    const [queues, setQueues] = useState<QueueInfo[]>([]);
    const [selectedQueueId, setSelectedQueueId] = useState("");
    const [flowRate, setFlowRate] = useState(10);
    const [batchSize, setBatchSize] = useState(1);
    const [adminUserId, setAdminUserId] = useState("");
    const [message, setMessage] = useState("");
    const [errorMessage, setErrorMessage] = useState("");
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function loadQueues() {
        try {
            setIsLoading(true);
            setErrorMessage("");

            const currentUser = await getCurrentUser();

            if (!currentUser) {
                setQueues([]);
                setSelectedQueueId("");
                setErrorMessage("You must be logged in as an admin.");
                return;
            }

            const result = await getActiveQueues(currentUser.id);
            const firstQueue = result[0];

            setAdminUserId(currentUser.id);
            setQueues(result);

            setSelectedQueueId((previous) => {
                if (previous && result.some((queue) => queue.id === previous)) {
                    return previous;
                }

                return firstQueue?.id ?? "";
            });

            if (firstQueue?.flowRatePerMinute) {
                setFlowRate(firstQueue.flowRatePerMinute);
            }
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Failed to load queues."));
        } finally {
            setIsLoading(false);
        }
    }

    useEffect(() => {
        loadQueues();
    }, []);

    useEffect(() => {
        const intervalId = window.setInterval(() => {
            loadQueues();
        }, 5000);

        return () => window.clearInterval(intervalId);
    }, []);

    function replaceQueue(updatedQueue: QueueInfo) {
        setQueues((previous) => {
            const exists = previous.some((queue) => queue.id === updatedQueue.id);

            if (!exists) {
                return [updatedQueue, ...previous];
            }

            return previous.map((queue) =>
                queue.id === updatedQueue.id ? updatedQueue : queue,
            );
        });
    }

    function handleQueueChange(queueId: string) {
        const selectedQueue = queues.find((queue) => queue.id === queueId);

        setSelectedQueueId(queueId);
        setFlowRate(selectedQueue?.flowRatePerMinute ?? flowRate);
        setMessage("");
        setErrorMessage("");
    }

    async function handleUpdateFlowRate() {
        if (!adminUserId || flowRate <= 0) {
            return;
        }

        try {
            setIsSubmitting(true);
            setMessage("");
            setErrorMessage("");

            await updateQueueFlowRate(adminUserId, selectedQueueId, flowRate);
            await loadQueues();

            setMessage("Global queue selector capacity was updated.");
        } catch (error) {
            setErrorMessage(
                getErrorMessage(error, "Failed to update queue selector capacity."),
            );
        } finally {
            setIsSubmitting(false);
        }
    }

    async function handleReleaseBatch() {
        if (!adminUserId || !selectedQueueId || batchSize <= 0) {
            return;
        }

        try {
            setIsSubmitting(true);
            setMessage("");
            setErrorMessage("");

            const updatedQueue = await releaseQueueBatch(
                adminUserId,
                selectedQueueId,
                batchSize,
            );

            replaceQueue(updatedQueue);
            setMessage(`Released up to ${batchSize} user(s) from the queue.`);
        } catch (error) {
            setErrorMessage(
                getErrorMessage(error, "Failed to release users from queue."),
            );
        } finally {
            setIsSubmitting(false);
        }
    }

    async function handleClearQueue() {
        if (!adminUserId || !selectedQueueId) {
            return;
        }

        try {
            setIsSubmitting(true);
            setMessage("");
            setErrorMessage("");

            const updatedQueue = await clearQueue(adminUserId, selectedQueueId);

            replaceQueue(updatedQueue);
            setMessage("Queue was cleared.");
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Failed to clear queue."));
        } finally {
            setIsSubmitting(false);
        }
    }

    const selectedQueue = queues.find((queue) => queue.id === selectedQueueId);
    const hasQueues = queues.length > 0;

    return (
        <main className="admin-page">
            <style>
                {`
                    .queue-admin-card {
                        width: min(1120px, 100%);
                        margin: 0 auto;
                        padding: 30px;
                        border: 1px solid #e5e7eb;
                        border-radius: 18px;
                        background: #ffffff;
                        box-shadow: 0 16px 35px rgba(15, 23, 42, 0.08);
                    }

                    .queue-admin-form {
                        display: grid;
                        gap: 26px;
                    }

                    .queue-admin-section-title {
                        margin: 0 0 -8px;
                        color: #111827;
                        font-size: 22px;
                        font-weight: 800;
                        text-align: left;
                    }

                    .queue-admin-help {
                        margin: -14px 0 0;
                        color: #64748b;
                        font-size: 15px;
                        text-align: left;
                    }

                    .queue-admin-field {
                        display: grid;
                        gap: 8px;
                    }

                    .queue-admin-field label {
                        color: #111827;
                        font-size: 16px;
                        font-weight: 800;
                    }

                    .queue-admin-field select,
                    .queue-admin-field input {
                        width: 100%;
                        height: 52px;
                        padding: 0 14px;
                        border: 1px solid #cbd5e1;
                        border-radius: 12px;
                        background: #ffffff;
                        color: #111827;
                        font-size: 16px;
                    }

                    .queue-admin-field select:focus,
                    .queue-admin-field input:focus {
                        outline: 2px solid #111827;
                        outline-offset: 2px;
                    }

                    .queue-admin-summary {
                        display: grid;
                        grid-template-columns: repeat(3, 1fr);
                        gap: 16px;
                    }

                    .queue-admin-summary-card {
                        padding: 20px;
                        border: 1px solid #e5e7eb;
                        border-radius: 16px;
                        background: #f8fafc;
                        text-align: center;
                    }

                    .queue-admin-summary-card span {
                        display: block;
                        margin-bottom: 8px;
                        color: #64748b;
                        font-size: 15px;
                        font-weight: 800;
                    }

                    .queue-admin-summary-card strong {
                        display: block;
                        color: #111827;
                        font-size: 30px;
                        line-height: 1;
                    }

                    .queue-admin-status {
                        text-transform: capitalize;
                    }

                    .queue-admin-controls-grid {
                        display: grid;
                        grid-template-columns: repeat(2, minmax(0, 1fr));
                        gap: 22px;
                        align-items: end;
                    }

                    .queue-admin-actions {
                        display: flex;
                        flex-wrap: wrap;
                        justify-content: center;
                        gap: 12px;
                        padding-top: 4px;
                    }

                    .queue-admin-button {
                        min-width: 150px;
                        padding: 12px 18px;
                        border-radius: 12px;
                        border: 1px solid #111827;
                        background: #111827;
                        color: #ffffff;
                        font: inherit;
                        font-size: 15px;
                        font-weight: 800;
                        cursor: pointer;
                        transition:
                            background 0.15s ease,
                            border-color 0.15s ease,
                            transform 0.08s ease,
                            box-shadow 0.15s ease;
                        box-shadow: 0 8px 18px rgba(15, 23, 42, 0.12);
                    }

                    .queue-admin-button:hover:not(:disabled) {
                        background: #374151;
                        border-color: #374151;
                        transform: translateY(-1px);
                    }

                    .queue-admin-button:disabled {
                        opacity: 0.6;
                        cursor: not-allowed;
                        box-shadow: none;
                    }

                    .queue-admin-button--secondary {
                        background: #ffffff;
                        color: #111827;
                        border-color: #cbd5e1;
                        box-shadow: none;
                    }

                    .queue-admin-button--secondary:hover:not(:disabled) {
                        background: #f3f4f6;
                        border-color: #94a3b8;
                    }

                    .queue-admin-button--danger {
                        background: #ffffff;
                        color: #991b1b;
                        border-color: rgba(220, 38, 38, 0.35);
                        box-shadow: none;
                    }

                    .queue-admin-button--danger:hover:not(:disabled) {
                        background: rgba(220, 38, 38, 0.08);
                        border-color: rgba(220, 38, 38, 0.55);
                    }

                    .queue-admin-message {
                        margin: 0;
                        padding: 14px 18px;
                        border-radius: 12px;
                        font-weight: 800;
                        text-align: center;
                    }

                    .queue-admin-message--success {
                        background: #ecfdf5;
                        border: 1px solid #a7f3d0;
                        color: #047857;
                    }

                    .queue-admin-message--error {
                        background: #fef2f2;
                        border: 1px solid #fecaca;
                        color: #b91c1c;
                    }

                    .queue-admin-empty-inline {
                        padding: 18px;
                        border: 1px dashed #cbd5e1;
                        border-radius: 14px;
                        background: #f8fafc;
                        color: #64748b;
                        text-align: center;
                    }

                    @media (max-width: 800px) {
                        .queue-admin-card {
                            padding: 22px;
                        }

                        .queue-admin-summary,
                        .queue-admin-controls-grid {
                            grid-template-columns: 1fr;
                        }

                        .queue-admin-button {
                            width: 100%;
                        }
                    }
                `}
            </style>

            <section className="page-header">
                <h1>Queue Monitoring and Control</h1>
                <p>
                    View active queues, release users manually, and control selector
                    capacity.
                </p>
            </section>

            {isLoading && (
                <section className="empty-state">
                    <h2>Loading queues...</h2>
                    <p>Please wait while we load the current queue state.</p>
                </section>
            )}

            {!isLoading && errorMessage && (
                <section className="empty-state">
                    <h2>Something went wrong</h2>
                    <p>{errorMessage}</p>
                </section>
            )}

            {!isLoading && !errorMessage && (
                <section className="queue-admin-card">
                    <div className="queue-admin-form">
                        <h2 className="queue-admin-section-title">
                            Global Queue Settings
                        </h2>

                        <p className="queue-admin-help">
                            These settings apply to the queue manager even when no
                            queue is currently active.
                        </p>

                        <div className="queue-admin-field">
                            <label htmlFor="flow-rate">
                                Max concurrent selectors
                            </label>
                            <input
                                id="flow-rate"
                                type="number"
                                min="1"
                                value={flowRate}
                                disabled={isSubmitting}
                                onChange={(event) =>
                                    setFlowRate(Number(event.target.value))
                                }
                            />
                        </div>

                        <div className="queue-admin-actions">
                            <button
                                type="button"
                                className="queue-admin-button"
                                onClick={handleUpdateFlowRate}
                                disabled={isSubmitting || flowRate <= 0}
                            >
                                Update Capacity
                            </button>

                            <button
                                type="button"
                                className="queue-admin-button queue-admin-button--secondary"
                                onClick={loadQueues}
                                disabled={isSubmitting}
                            >
                                Refresh
                            </button>
                        </div>

                        <h2 className="queue-admin-section-title">
                            Active Queue Control
                        </h2>

                        {!hasQueues && (
                            <div className="queue-admin-empty-inline">
                                There are currently no active queues. Lower the max
                                concurrent selectors above, then start checkout from
                                two sessions to create a queue.
                            </div>
                        )}

                        {hasQueues && (
                            <>
                                <div className="queue-admin-field">
                                    <label htmlFor="queue-select">Queue</label>
                                    <select
                                        id="queue-select"
                                        value={selectedQueueId}
                                        onChange={(event) =>
                                            handleQueueChange(event.target.value)
                                        }
                                        disabled={isSubmitting}
                                    >
                                        {queues.map((queue) => (
                                            <option key={queue.id} value={queue.id}>
                                                {queue.eventName} —{" "}
                                                {queue.waitingUsers} waiting,{" "}
                                                {queue.activeSelectorsCount} selecting
                                            </option>
                                        ))}
                                    </select>
                                </div>

                                {selectedQueue && (
                                    <div className="queue-admin-summary">
                                        <div className="queue-admin-summary-card">
                                            <span>Waiting users</span>
                                            <strong>{selectedQueue.waitingUsers}</strong>
                                        </div>

                                        <div className="queue-admin-summary-card">
                                            <span>Active selectors</span>
                                            <strong>
                                                {selectedQueue.activeSelectorsCount}
                                            </strong>
                                        </div>

                                        <div className="queue-admin-summary-card">
                                            <span>Status</span>
                                            <strong className="queue-admin-status">
                                                {selectedQueue.status}
                                            </strong>
                                        </div>
                                    </div>
                                )}

                                <div className="queue-admin-controls-grid">
                                    <div className="queue-admin-field">
                                        <label htmlFor="batch-size">
                                            Manual release batch size
                                        </label>
                                        <input
                                            id="batch-size"
                                            type="number"
                                            min="1"
                                            value={batchSize}
                                            disabled={isSubmitting}
                                            onChange={(event) =>
                                                setBatchSize(Number(event.target.value))
                                            }
                                        />
                                    </div>
                                </div>

                                <div className="queue-admin-actions">
                                    <button
                                        type="button"
                                        className="queue-admin-button"
                                        onClick={handleReleaseBatch}
                                        disabled={
                                            isSubmitting ||
                                            !selectedQueueId ||
                                            batchSize <= 0
                                        }
                                    >
                                        Release Batch
                                    </button>

                                    <button
                                        type="button"
                                        className="queue-admin-button queue-admin-button--danger"
                                        onClick={handleClearQueue}
                                        disabled={isSubmitting || !selectedQueueId}
                                    >
                                        Clear Queue
                                    </button>
                                </div>
                            </>
                        )}

                        {message && (
                            <p className="queue-admin-message queue-admin-message--success">
                                {message}
                            </p>
                        )}
                    </div>
                </section>
            )}
        </main>
    );
}