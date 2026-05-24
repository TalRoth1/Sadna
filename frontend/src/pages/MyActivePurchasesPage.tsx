import { useEffect, useMemo, useState } from "react";
import {
    cancelActivePurchase,
    getActivePurchasesForUser,
    type ActivePurchaseResponse,
} from "../services/purchaseService";
import { getStoredUserId } from "../services/authService";

type MyActivePurchasesPageProps = {
    onOpenPurchase: (eventId: string) => void;
};

function formatDateTime(date: string | null): string {
    if (!date) {
        return "Unknown date";
    }

    const parsed = new Date(date);
    if (Number.isNaN(parsed.getTime())) {
        return date;
    }

    return parsed.toLocaleString("he-IL", {
        dateStyle: "short",
        timeStyle: "short",
    });
}

function formatTimeRemaining(endTime: string, now: number): string {
    const end = new Date(endTime).getTime();
    const msRemaining = end - now;

    if (!Number.isFinite(end) || msRemaining <= 0) {
        return "Expired";
    }

    const totalSeconds = Math.floor(msRemaining / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;

    return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function getTimerTone(endTime: string, now: number): "normal" | "warning" | "critical" | "expired" {
    const end = new Date(endTime).getTime();
    const secondsRemaining = Math.floor((end - now) / 1000);

    if (!Number.isFinite(end) || secondsRemaining <= 0) {
        return "expired";
    }

    if (secondsRemaining < 60) {
        return "critical";
    }

    if (secondsRemaining < 180) {
        return "warning";
    }

    return "normal";
}

export default function MyActivePurchasesPage({
    onOpenPurchase,
}: MyActivePurchasesPageProps) {
    const [activePurchases, setActivePurchases] = useState<ActivePurchaseResponse[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState("");
    const [isCancellingId, setIsCancellingId] = useState<string | null>(null);
    const [now, setNow] = useState(() => Date.now());

    useEffect(() => {
        const intervalId = window.setInterval(() => {
            setNow(Date.now());
        }, 1000);

        return () => window.clearInterval(intervalId);
    }, []);

    useEffect(() => {
        async function loadActivePurchases() {
            try {
                setIsLoading(true);
                setErrorMessage("");

                const userId = getStoredUserId();

                if (!userId) {
                    setActivePurchases([]);
                    setErrorMessage("No active session was found.");
                    return;
                }

                const purchases = await getActivePurchasesForUser(userId);
                setActivePurchases(purchases);
            } catch (error) {
                setErrorMessage(
                    error instanceof Error
                        ? error.message
                        : "Failed to load active purchases.",
                );
            } finally {
                setIsLoading(false);
            }
        }

        loadActivePurchases();
    }, []);

    const visibleActivePurchases = useMemo(
        () =>
            activePurchases.filter((purchase) => {
                const end = new Date(purchase.endTime).getTime();
                return Number.isFinite(end) && end > now;
            }),
        [activePurchases, now],
    );

    async function handleCancel(activePurchaseId: string) {
        try {
            setIsCancellingId(activePurchaseId);
            setErrorMessage("");

            await cancelActivePurchase(activePurchaseId);

            setActivePurchases((previous) =>
                previous.filter(
                    (purchase) => purchase.activePurchaseId !== activePurchaseId,
                ),
            );
        } catch (error) {
            setErrorMessage(
                error instanceof Error
                    ? error.message
                    : "Failed to cancel reservation.",
            );
        } finally {
            setIsCancellingId(null);
        }
    }

    return (
        <main className="purchase-history-page">
            <style>
                {`
                    .active-purchase-card {
                        align-items: stretch;
                    }

                    .active-purchase-main {
                        flex: 1;
                    }

                    .active-purchase-main h2 {
                        margin-bottom: 14px;
                    }

                    .active-purchase-meta {
                        display: grid;
                        gap: 8px;
                        color: #374151;
                        font-size: 17px;
                    }

                    .active-purchase-meta p {
                        margin: 0;
                    }

                    .active-purchase-timer {
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        margin-top: 16px;
                        padding: 10px 16px;
                        border-radius: 999px;
                        font-weight: 800;
                        font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                        letter-spacing: 0.4px;
                    }

                    .active-purchase-timer.tone-normal {
                        color: #1d4ed8;
                        background: rgba(59, 130, 246, 0.12);
                        border: 1px solid rgba(59, 130, 246, 0.28);
                    }

                    .active-purchase-timer.tone-warning {
                        color: #92400e;
                        background: rgba(251, 191, 36, 0.18);
                        border: 1px solid rgba(251, 191, 36, 0.5);
                    }

                    .active-purchase-timer.tone-critical {
                        color: #991b1b;
                        background: rgba(220, 38, 38, 0.12);
                        border: 1px solid rgba(220, 38, 38, 0.35);
                    }

                    .active-purchase-actions {
                        min-width: 210px;
                        display: flex;
                        flex-direction: column;
                        justify-content: center;
                        gap: 12px;
                    }

                    .active-purchase-button {
                        width: 100%;
                        padding: 12px 18px;
                        border-radius: 12px;
                        font: inherit;
                        font-size: 15px;
                        font-weight: 800;
                        cursor: pointer;
                        transition:
                            transform 0.08s ease,
                            box-shadow 0.15s ease,
                            background 0.15s ease,
                            border-color 0.15s ease;
                    }

                    .active-purchase-button:hover:not(:disabled) {
                        transform: translateY(-1px);
                    }

                    .active-purchase-button:active:not(:disabled) {
                        transform: translateY(0);
                    }

                    .active-purchase-button:disabled {
                        opacity: 0.65;
                        cursor: not-allowed;
                    }

                    .active-purchase-button--primary {
                        color: #ffffff;
                        background: #111827;
                        border: 1px solid #111827;
                        box-shadow: 0 8px 18px rgba(17, 24, 39, 0.18);
                    }

                    .active-purchase-button--primary:hover:not(:disabled) {
                        background: #374151;
                        border-color: #374151;
                    }

                    .active-purchase-button--danger {
                        color: #991b1b;
                        background: #ffffff;
                        border: 1px solid rgba(220, 38, 38, 0.35);
                    }

                    .active-purchase-button--danger:hover:not(:disabled) {
                        background: rgba(220, 38, 38, 0.08);
                        border-color: rgba(220, 38, 38, 0.55);
                    }

                    @media (max-width: 700px) {
                        .active-purchase-actions {
                            min-width: unset;
                            width: 100%;
                        }
                    }
                `}
            </style>

            <section className="page-header">
                <h1>My Active Purchases</h1>
                <p>Resume or cancel ticket reservations that are still in progress.</p>
            </section>

            {isLoading && (
                <section className="empty-state">
                    <h2>Loading active purchases...</h2>
                    <p>Please wait while we load your current reservations.</p>
                </section>
            )}

            {!isLoading && errorMessage && (
                <section className="empty-state">
                    <h2>Something went wrong</h2>
                    <p>{errorMessage}</p>
                </section>
            )}

            {!isLoading && !errorMessage && visibleActivePurchases.length === 0 && (
                <section className="empty-state">
                    <h2>No active purchases</h2>
                    <p>You do not currently have any reserved tickets.</p>
                </section>
            )}

            {!isLoading && !errorMessage && visibleActivePurchases.length > 0 && (
                <div className="history-content">
                    <section className="history-section">
                        <h2>Current Reservations</h2>

                        <div className="purchase-list">
                            {visibleActivePurchases.map((purchase) => {
                                const timerTone = getTimerTone(purchase.endTime, now);
                                const timeRemaining = formatTimeRemaining(
                                    purchase.endTime,
                                    now,
                                );

                                return (
                                    <article
                                        key={purchase.activePurchaseId}
                                        className="purchase-card active-purchase-card"
                                    >
                                        <div className="active-purchase-main">
                                            <h2>{purchase.eventName || "Untitled event"}</h2>

                                            <div className="active-purchase-meta">
                                                <p>
                                                    <strong>Event date:</strong>{" "}
                                                    {formatDateTime(purchase.eventDate)}
                                                </p>
                                                <p>
                                                    <strong>Location:</strong>{" "}
                                                    {purchase.eventLocation || "Unknown location"}
                                                </p>
                                                <p>
                                                    <strong>Tickets reserved:</strong>{" "}
                                                    {purchase.ticketsAmount}
                                                </p>
                                                <p>
                                                    <strong>Total:</strong> {purchase.price} NIS
                                                </p>
                                            </div>

                                            <div
                                                className={`active-purchase-timer tone-${timerTone}`}
                                                role="timer"
                                                aria-live="polite"
                                            >
                                                Time remaining: {timeRemaining}
                                            </div>
                                        </div>

                                        <div className="active-purchase-actions">
                                            <button
                                                type="button"
                                                className="active-purchase-button active-purchase-button--primary"
                                                onClick={() => onOpenPurchase(purchase.eventId)}
                                            >
                                                Continue checkout
                                            </button>

                                            <button
                                                type="button"
                                                className="active-purchase-button active-purchase-button--danger"
                                                onClick={() =>
                                                    handleCancel(purchase.activePurchaseId)
                                                }
                                                disabled={
                                                    isCancellingId === purchase.activePurchaseId
                                                }
                                            >
                                                {isCancellingId === purchase.activePurchaseId
                                                    ? "Cancelling..."
                                                    : "Cancel reservation"}
                                            </button>
                                        </div>
                                    </article>
                                );
                            })}
                        </div>
                    </section>
                </div>
            )}
        </main>
    );
}