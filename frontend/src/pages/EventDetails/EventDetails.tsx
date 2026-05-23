import { useEffect, useMemo, useState } from "react";
import { getEventById } from "../../services/eventSearchService";
import {
    getAvailableTicketsCount,
    getPriceRange,
    isEventBookable,
    type DiscountRule,
    type Event,
    type EventStatus,
    type PurchasePolicy,
} from "../../types/event";
import "./EventDetails.css";

type EventDetailsPageProps = {
    eventId: string;
    onBackToSearch: () => void;
    onStartPurchase: (eventId: string) => void;
    onBackToCompany?: (companyId: string) => void;
    onStartLotteryRegistration: (eventId: string) => void;
};


type ActionResultMessage = {
    kind: "success" | "error";
    text: string;
};

type PrimaryAction =
    | { kind: "buy"; availableTickets: number }
    | { kind: "lottery"; lotteryId: string }
    | { kind: "queue" }
    | { kind: "unavailable"; reason: string };

function formatEventDateTime(date: string) {
    const eventDate = new Date(date);
    if (Number.isNaN(eventDate.getTime())) {
        return "Invalid date";
    }
    return eventDate.toLocaleString("he-IL", {
        dateStyle: "full",
        timeStyle: "short",
    });
}

function formatPriceRange(event: Event): string {
    const { min, max } = getPriceRange(event);
    if (min === max) {
        return `${min} NIS`;
    }
    return `${min}–${max} NIS`;
}

function formatDateRange(fromDate: string, toDate: string): string {
    const formatPart = (raw: string) => {
        const parsed = new Date(raw);
        if (Number.isNaN(parsed.getTime())) {
            return raw;
        }
        return parsed.toLocaleDateString("he-IL");
    };
    return `${formatPart(fromDate)} – ${formatPart(toDate)}`;
}

function describeEventStatus(status: EventStatus): {
    label: string;
    tone: "active" | "ended" | "canceled";
} {
    switch (status) {
        case "ACTIVE":
            return { label: "On sale", tone: "active" };
        case "ENDED":
            return { label: "Ended", tone: "ended" };
        case "CANCELED":
            return { label: "Cancelled", tone: "canceled" };
    }
}

function describePurchaseRules(policy: PurchasePolicy): string[] {
    const lines: string[] = [];
    if (policy.minAge !== undefined) {
        lines.push(`Minimum age: ${policy.minAge}+`);
    }
    if (policy.minTicketsPerPurchase !== undefined) {
        lines.push(
            `Minimum tickets per purchase: ${policy.minTicketsPerPurchase}`,
        );
    }
    if (policy.maxTicketsPerPurchase !== undefined) {
        lines.push(
            `Maximum tickets per purchase: ${policy.maxTicketsPerPurchase}`,
        );
    }
    if (policy.allowLoneSeat !== undefined) {
        lines.push(
            policy.allowLoneSeat
                ? "Lone seats allowed"
                : "No lone seats — adjacent seating required",
        );
    }
    if (lines.length === 0) {
        lines.push("No specific purchase restrictions.");
    }
    return lines;
}

function describeDiscountRule(rule: DiscountRule): string {
    const range = formatDateRange(rule.fromDate, rule.toDate);
    if (rule.kind === "OVERT") {
        return `${rule.percent}% off (${range})`;
    }
    if (rule.kind === "CONDITIONAL") {
        const requirement =
            rule.requiredTickets !== undefined && rule.appliedTickets !== undefined
                ? `Buy ${rule.requiredTickets}, get ${rule.appliedTickets} at ${rule.percent}% off`
                : `${rule.percent}% off when conditions are met`;
        return `${requirement} (${range})`;
    }
    const code = rule.code ? ` (code: ${rule.code})` : "";
    return `Coupon — ${rule.percent}% off${code} (${range})`;
}

function decidePrimaryAction(event: Event): PrimaryAction {
    if (!isEventBookable(event)) {
        const reason =
            event.status === "CANCELED"
                ? "This event has been cancelled."
                : "This event has already ended.";
        return { kind: "unavailable", reason };
    }
    if (event.lotteryId) {
        return { kind: "lottery", lotteryId: event.lotteryId };
    }
    const availableTickets = getAvailableTicketsCount(event);
    if (availableTickets > 0) {
        return { kind: "buy", availableTickets };
    }
    return { kind: "queue" };
}

type StatusBadgeProps = {
    status: EventStatus;
};

function StatusBadge({ status }: StatusBadgeProps) {
    const { label, tone } = describeEventStatus(status);
    return <span className={`event-status-badge tone-${tone}`}>{label}</span>;
}

type DetailRowProps = {
    label: string;
    value: string;
};

function DetailRow({ label, value }: DetailRowProps) {
    return (
        <div className="event-details-row">
            <dt>{label}</dt>
            <dd>{value}</dd>
        </div>
    );
}

export default function EventDetailsPage({
    eventId,
    onBackToSearch,
    onStartPurchase,
    onStartLotteryRegistration,
    onBackToCompany,
}: EventDetailsPageProps) {

    const [event, setEvent] = useState<Event | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState("");
    const [actionMessage, setActionMessage] =
        useState<ActionResultMessage | null>(null);
    const [isPerformingAction, setIsPerformingAction] = useState(false);

    useEffect(() => {
        let isCancelled = false;

        async function loadEvent() {
            try {
                setIsLoading(true);
                setErrorMessage("");
                setActionMessage(null);

                const result = await getEventById(eventId);
                if (isCancelled) {
                    return;
                }
                if (!result) {
                    setErrorMessage("This event could not be found.");
                    setEvent(null);
                    return;
                }
                setEvent(result);
            } catch {
                if (!isCancelled) {
                    setErrorMessage("Failed to load event details.");
                }
            } finally {
                if (!isCancelled) {
                    setIsLoading(false);
                }
            }
        }

        loadEvent();
        return () => {
            isCancelled = true;
        };
    }, [eventId]);

    const primaryAction = useMemo<PrimaryAction | null>(
        () => (event ? decidePrimaryAction(event) : null),
        [event],
    );

    function handlePrimaryActionClick() {
        if (!event || !primaryAction || primaryAction.kind === "unavailable") {
            return;
        }

        // The "buy" branch routes to the Ticket Purchase Screen (#87) where
        // the actual seat selection happens. Lottery / queue stay mocked here
        // until those flows ship.
        if (primaryAction.kind === "buy") {
            onStartPurchase(event.id);
            return;
        }

        if (primaryAction.kind === "lottery") {
            onStartLotteryRegistration(event.id);
            return;
        }

        // TODO: Replace with the real flow once the protocol/API is implemented:
        //   queue -> POST /events/{id}/queue
        // (The lottery path used to be mocked here too, but it now goes
        // through the real onStartLotteryRegistration call above.)
        setIsPerformingAction(true);
        setActionMessage(null);
        setTimeout(() => {
            setIsPerformingAction(false);
            if (primaryAction.kind === "queue") {
                setActionMessage({
                    kind: "success",
                    text: "You're in the queue. We'll let you know as soon as a ticket becomes available.",
                });
            }
        }, 350);
    }

    if (isLoading) {
        return (
            <main className="app-page event-details-page">
                <section className="empty-state">
                    <h2>Loading event…</h2>
                    <p>Please wait while we load the event details.</p>
                </section>
            </main>
        );
    }

    if (errorMessage || !event) {
        return (
            <main className="app-page event-details-page">
                <section className="empty-state">
                    <h2>Event unavailable</h2>
                    <p>{errorMessage || "This event could not be found."}</p>
                    <button
                        type="button"
                        className="event-action-secondary"
                        onClick={onBackToSearch}
                    >
                        Back to search
                    </button>
                </section>
            </main>
        );
    }

    const availableTickets = getAvailableTicketsCount(event);
    const priceRange = formatPriceRange(event);
    const bookable = isEventBookable(event);

    return (
        <main className="app-page event-details-page">
            <section className="event-details-header">
                <button
                    type="button"
                    className="event-back-link"
                    onClick={onBackToSearch}
                >
                    ← Back to search
                </button>

                <div className="event-details-title-row">
                    <div>
                        <h1>{event.name}</h1>
                        <p className="event-details-subtitle">
                            {event.artist} · {event.type}
                        </p>
                    </div>
                    <StatusBadge status={event.status} />
                </div>
            </section>

            {!bookable && (
                <section className="event-closed-banner" role="status">
                    <h2>Event is currently unavailable</h2>
                    <p>
                        {event.status === "CANCELED"
                            ? "This event has been cancelled. New actions cannot be taken."
                            : "This event has already ended. New actions cannot be taken."}
                    </p>
                </section>
            )}

            <div className="event-details-grid">
                <section className="event-details-card">
                    <h2>Event details</h2>
                    <dl className="event-details-list">
                        <DetailRow
                            label="Date & time"
                            value={formatEventDateTime(event.date)}
                        />
                        <DetailRow label="Location" value={event.location} />
                        <DetailRow label="Category" value={event.type} />
                        <DetailRow label="Artist" value={event.artist} />
                        <DetailRow label="Production" value={event.companyName} />
                        <DetailRow label="Price" value={priceRange} />
                        <DetailRow
                            label="Available tickets"
                            value={
                                bookable
                                    ? `${availableTickets} of ${event.tickets.length}`
                                    : "—"
                            }
                        />
                        <DetailRow
                            label="Rating"
                            value={`★ ${event.rating.toFixed(1)} / 5`}
                        />
                    </dl>

                    {event.tags.length > 0 && (
                        <div className="event-tag-list">
                            {event.tags.map((tag) => (
                                <span key={tag} className="event-tag">
                                    {tag}
                                </span>
                            ))}
                        </div>
                    )}
                </section>

                <section className="event-details-card">
                    <h2>About this event</h2>
                    <p className="event-description">
                        {event.description ??
                            "No description has been provided for this event yet."}
                    </p>
                </section>

                <section className="event-details-card">
                    <h2>Event policy</h2>
                    <h3>Purchase rules</h3>
                    <ul className="event-policy-list">
                        {describePurchaseRules(event.purchasePolicy).map(
                            (line) => (
                                <li key={line}>{line}</li>
                            ),
                        )}
                    </ul>

                    <h3>Discounts</h3>
                    {event.discountPolicy.rules.length === 0 ? (
                        <p className="event-policy-empty">
                            No active discounts for this event.
                        </p>
                    ) : (
                        <ul className="event-policy-list">
                            {event.discountPolicy.rules.map((rule) => (
                                <li key={rule.id}>{describeDiscountRule(rule)}</li>
                            ))}
                        </ul>
                    )}
                </section>

                <section className="event-details-card event-actions-card">
                    <h2>Actions</h2>

                    {primaryAction && primaryAction.kind === "buy" && (
                        <button
                            type="button"
                            className="event-action-primary"
                            onClick={handlePrimaryActionClick}
                            disabled={isPerformingAction}
                        >
                            {isPerformingAction
                                ? "Reserving…"
                                : `Buy tickets · ${priceRange}`}
                        </button>
                    )}
                    {primaryAction && primaryAction.kind === "lottery" && (
                        <button
                            type="button"
                            className="event-action-primary"
                            onClick={handlePrimaryActionClick}
                            disabled={isPerformingAction}
                        >
                            {isPerformingAction
                                ? "Registering…"
                                : "Register for the lottery"}
                        </button>
                    )}
                    {primaryAction && primaryAction.kind === "queue" && (
                        <button
                            type="button"
                            className="event-action-primary"
                            onClick={handlePrimaryActionClick}
                            disabled={isPerformingAction}
                        >
                            {isPerformingAction
                                ? "Joining queue…"
                                : "Join the queue"}
                        </button>
                    )}
                    {primaryAction && primaryAction.kind === "unavailable" && (
                        <p className="event-action-disabled-text">
                            {primaryAction.reason}
                        </p>
                    )}

                    {actionMessage && (
                        <div
                            className={`event-action-message tone-${actionMessage.kind}`}
                            role={
                                actionMessage.kind === "error" ? "alert" : "status"
                            }
                        >
                            {actionMessage.text}
                        </div>
                    )}

                    <div className="event-action-secondary-row">
                        <button
                            type="button"
                            className="event-action-secondary"
                            onClick={onBackToSearch}
                        >
                            Back to search
                        </button>
                        {onBackToCompany && (
                            <button
                                type="button"
                                className="event-action-secondary"
                                onClick={() => onBackToCompany(event.companyId)}
                            >
                                Back to {event.companyName}
                            </button>
                        )}
                    </div>
                </section>
            </div>
        </main>
    );
}
