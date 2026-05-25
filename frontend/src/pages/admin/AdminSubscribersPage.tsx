import { useEffect, useMemo, useState } from "react";
import {
    getSubscribers,
    removeSubscriber,
} from "../../services/admin/adminSubscribersService";
import { getCurrentUser } from "../../services/currentUserService";
import type { Subscriber } from "../../types/admin";

function getErrorMessage(error: unknown): string {
    if (
        typeof error === "object" &&
        error !== null &&
        "response" in error
    ) {
        const axiosError = error as {
            response?: {
                data?: {
                    message?: string;
                };
            };
        };

        return axiosError.response?.data?.message ?? "Failed to remove subscriber.";
    }

    return "Failed to remove subscriber.";
}

export default function AdminSubscribersPage() {
    const [subscribers, setSubscribers] = useState<Subscriber[]>([]);
    const [selectedSubscriberId, setSelectedSubscriberId] = useState("");
    const [statusMessage, setStatusMessage] = useState("");
    const [errorMessage, setErrorMessage] = useState("");
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const selectedSubscriber = useMemo(
        () =>
            subscribers.find(
                (subscriber) => subscriber.username === selectedSubscriberId,
            ) ?? null,
        [subscribers, selectedSubscriberId],
    );

    useEffect(() => {
        loadSubscribers();
    }, []);

    async function loadSubscribers() {
        setIsLoading(true);
        setStatusMessage("");
        setErrorMessage("");

        try {
            const currentUser = await getCurrentUser();

            if (!currentUser) {
                setSubscribers([]);
                setSelectedSubscriberId("");
                setErrorMessage("You must be logged in as admin.");
                return;
            }

            const loadedSubscribers = await getSubscribers(currentUser.id);

            setSubscribers(loadedSubscribers);
            setSelectedSubscriberId(loadedSubscribers[0]?.id ?? "");
        } catch {
            setSubscribers([]);
            setSelectedSubscriberId("");
            setErrorMessage("Failed to load subscribers.");
        } finally {
            setIsLoading(false);
        }
    }

    async function handleRemoveSubscriber() {
        setStatusMessage("");
        setErrorMessage("");

        if (!selectedSubscriberId) {
            setErrorMessage("Select an active subscriber first.");
            return;
        }

        setIsSubmitting(true);

        try {
            const currentUser = await getCurrentUser();

            if (!currentUser) {
                setErrorMessage("You must be logged in as admin.");
                return;
            }

            await removeSubscriber(currentUser.id, selectedSubscriberId);

            const updatedSubscribers = await getSubscribers(currentUser.id);

            setSubscribers(updatedSubscribers);
            setSelectedSubscriberId(updatedSubscribers[0]?.id ?? "");
            setStatusMessage("Subscriber was removed successfully.");
        } catch (error) {
            setErrorMessage(getErrorMessage(error));
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <main className="page-shell admin-page-shell">
            <section className="page-header">
                <h1>Remove Subscriber</h1>
                <p>Remove a registered subscriber from the platform.</p>
            </section>

            <section className="admin-form-card">
                {isLoading ? (
                    <p className="empty-state">Loading subscribers...</p>
                ) : subscribers.length === 0 ? (
                    <p className="empty-state">
                        There are no active subscribers to remove.
                    </p>
                ) : (
                    <>
                        <div className="admin-field-group">
                            <label
                                className="admin-field-label"
                                htmlFor="subscriber-select"
                            >
                                Active subscriber
                            </label>

                            <select
                                id="subscriber-select"
                                className="admin-select"
                                value={selectedSubscriberId}
                                onChange={(event) => {
                                    setSelectedSubscriberId(event.target.value);
                                    setStatusMessage("");
                                    setErrorMessage("");
                                }}
                            >
                                {subscribers.map((subscriber) => (
                                    <option key={subscriber.id} value={subscriber.username}>
                                        {subscriber.username} - {subscriber.email}
                                    </option>
                                ))}
                            </select>
                        </div>

                        {selectedSubscriber && (
                            <div className="admin-details-box">
                                <div className="admin-detail-row">
                                    <span>Username</span>
                                    <strong>{selectedSubscriber.username}</strong>
                                </div>

                                <div className="admin-detail-row">
                                    <span>Email</span>
                                    <strong>{selectedSubscriber.email}</strong>
                                </div>

                                <div className="admin-detail-row">
                                    <span>Status</span>
                                    <strong>{selectedSubscriber.status}</strong>
                                </div>
                            </div>
                        )}

                        <button
                            type="button"
                            className="admin-primary-button"
                            onClick={handleRemoveSubscriber}
                            disabled={isSubmitting}
                        >
                            {isSubmitting ? "Removing..." : "Remove Subscriber"}
                        </button>
                    </>
                )}

                {statusMessage && (
                    <div className="success-message">{statusMessage}</div>
                )}

                {errorMessage && (
                    <div className="error-message">{errorMessage}</div>
                )}
            </section>
        </main>
    );
}