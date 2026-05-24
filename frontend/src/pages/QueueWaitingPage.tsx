import { useEffect, useState } from "react";
import { getEventById } from "../services/eventSearchService";
import {
    getSelectionAccessStatus,
    requestSelectionAccess,
} from "../services/selectionAccessService";
import { ensureGuestSession } from "../services/authService";

type QueueWaitingPageProps = {
    eventId: string;
    onBackToEvent: () => void;
    onAccessGranted: (accessExpiresAt: string | null) => void;
};

const POLL_INTERVAL_MS = 3000;

function formatAccessDeadline(value: string | null): string {
    if (!value) {
        return "Unknown";
    }

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
        return value;
    }

    return parsed.toLocaleString("he-IL", {
        dateStyle: "short",
        timeStyle: "short",
    });
}

export default function QueueWaitingPage({
    eventId,
    onBackToEvent,
    onAccessGranted,
}: QueueWaitingPageProps) {
    const [event, setEvent] = useState<Event | null>(null);
    const [userId, setUserId] = useState<string | null>(null);
    const [access, setAccess] = useState<SelectionAccess | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        let cancelled = false;

        async function enterQueue() {
            try {
                setIsLoading(true);
                setErrorMessage("");

                const [session, loadedEvent] = await Promise.all([
                    ensureGuestSession(),
                    getEventById(eventId),
                ]);

                if (cancelled) {
                    return;
                }

                setUserId(session.userId);
                setEvent(loadedEvent);

                const initialAccess = await requestSelectionAccess(
                    eventId,
                    session.userId,
                );

                if (cancelled) {
                    return;
                }

                setAccess(initialAccess);

                if (initialAccess.allowed) {
                    onAccessGranted(initialAccess.accessExpiresAt);
                }
            } catch (error) {
                if (!cancelled) {
                    setErrorMessage(
                        error instanceof Error
                            ? error.message
                            : "Failed to enter queue.",
                    );
                }
            } finally {
                if (!cancelled) {
                    setIsLoading(false);
                }
            }
        }

        enterQueue();

        return () => {
            cancelled = true;
        };
    }, [eventId, onAccessGranted]);

    useEffect(() => {
        if (!userId || access?.allowed) {
            return;
        }

        let cancelled = false;

        const intervalId = window.setInterval(async () => {
            try {
                const latest = await getSelectionAccessStatus(eventId, userId);

                if (cancelled) {
                    return;
                }

                setAccess(latest);

                if (latest.allowed) {
                    onAccessGranted(latest.accessExpiresAt);
                }
            } catch (error) {
                if (!cancelled) {
                    setErrorMessage(
                        error instanceof Error
                            ? error.message
                            : "Failed to refresh queue status.",
                    );
                }
            }
        }, POLL_INTERVAL_MS);

        return () => {
            cancelled = true;
            window.clearInterval(intervalId);
        };
    }, [access?.allowed, eventId, onAccessGranted, userId]);

    return (
        <main className="app-page">
            <section className="page-header">
                <h1>Virtual Queue</h1>
                <p>
                    {event
                        ? `Waiting room for ${event.name}`
                        : "Waiting room for ticket selection"}
                </p>
            </section>

            <section
                style={{
                    maxWidth: 760,
                    margin: "0 auto",
                    padding: 28,
                    borderRadius: 18,
                    border: "1px solid #e5e7eb",
                    background: "#ffffff",
                    boxShadow: "0 16px 35px rgba(15, 23, 42, 0.08)",
                    textAlign: "center",
                }}
            >
                {isLoading && (
                    <>
                        <h2>Checking queue availability...</h2>
                        <p>Please wait while we request access to ticket selection.</p>
                    </>
                )}

                {!isLoading && errorMessage && (
                    <>
                        <h2>Could not enter queue</h2>
                        <p>{errorMessage}</p>
                    </>
                )}

                {!isLoading && !errorMessage && access && !access.allowed && (
                    <>
                        <h2>You are waiting in queue</h2>
                        {access.positionInQueue > 0 ? (
                            <p>
                                Position {access.positionInQueue} of {access.queueSize}
                            </p>
                        ) : (
                            <p>You are not currently allowed to select tickets.</p>
                        )}
                        <p>
                            This page updates automatically. When your turn arrives,
                            you will be moved into ticket selection.
                        </p>
                    </>
                )}

                {!isLoading && !errorMessage && access?.allowed && (
                    <>
                        <h2>Your turn has arrived</h2>
                        <p>
                            Selection access expires at {formatAccessDeadline(access.accessExpiresAt)}.
                        </p>
                    </>
                )}

                <button
                    type="button"
                    className="admin-secondary-button"
                    onClick={onBackToEvent}
                    style={{ marginTop: 20 }}
                >
                    Back to event details
                </button>
            </section>
        </main>
    );
}
