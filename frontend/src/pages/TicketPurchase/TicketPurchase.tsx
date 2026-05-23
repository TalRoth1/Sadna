import { useEffect, useMemo, useRef, useState } from "react";
import { getEventById } from "../../services/eventSearchService";

import { getCurrentUser } from "../../services/currentUserService";
import {
    selectSittingTickets,
    selectStandingTickets,
    type ActivePurchaseDTO,
} from "../../services/ticketPurchaseService";

import {
    isEventBookable,
    type Area,
    type Event,
    type Ticket,
} from "../../types/event";
import "./TicketPurchase.css";

// ---------------------------------------------------------------------------
// Types & constants
// ---------------------------------------------------------------------------



type TicketPurchasePageProps = {
    eventId: string;
    onBackToEvent: () => void;
};

// Shape proposed in Phase 1: sitting tickets are tracked by exact id (matches
// Event.reserveSittingTickets(List<UUID>) on the backend), standing tickets
// are tracked by area + desired count (matches
// Event.reserveStandingTickets(int amount, UUID areaId)).
type SeatSelection = {
    sittingTicketIds: Record<string, true>;
    standingCountByArea: Record<string, number>;
};

const EMPTY_SELECTION: SeatSelection = {
    sittingTicketIds: {},
    standingCountByArea: {},
};

type ActionResultMessage = {
    kind: "success" | "error";
    text: string;
};

type LoneSeatLocation = {
    areaName: string;
    row: number;
    seat: number;
};

type ValidationResult = {
    blockers: string[];
    warnings: string[];
};

const PURCHASE_WINDOW_MINUTES = 10;
const MOCK_RESERVATION_DELAY_MS = 400;

// Pattern B: the user browses freely in "select" mode (no backend lock,
// no timer) and only commits to a reservation when they click "Continue
// to checkout". That commit is what mints the reservationDeadline and
// flips us into "checkout" mode — mirroring how the backend's
// PurchaseService.select{Sitting,Standing}Tickets creates the
// ActivePurchase with a fixed endTime.
type PurchaseStep = "select" | "checkout";

// ---------------------------------------------------------------------------
// Pure helpers — small, side-effect-free, easy to reason about / test later.
// ---------------------------------------------------------------------------

function getSelectedTicketIds(selection: SeatSelection): string[] {
    return Object.keys(selection.sittingTicketIds);
}

function getTotalSelectedCount(selection: SeatSelection): number {
    const sittingCount = getSelectedTicketIds(selection).length;
    const standingCount = Object.values(selection.standingCountByArea).reduce(
        (sum, count) => sum + count,
        0,
    );
    return sittingCount + standingCount;
}

function getTotalPrice(selection: SeatSelection, event: Event): number {
    const ticketsById = new Map(event.tickets.map((ticket) => [ticket.id, ticket]));
    const sittingTotal = getSelectedTicketIds(selection).reduce(
        (sum, ticketId) => sum + (ticketsById.get(ticketId)?.price ?? 0),
        0,
    );

    const areasById = new Map(event.layout.areas.map((area) => [area.id, area]));
    const standingTotal = Object.entries(selection.standingCountByArea).reduce(
        (sum, [areaId, count]) => sum + (areasById.get(areaId)?.price ?? 0) * count,
        0,
    );

    return sittingTotal + standingTotal;
}

function getTicketsByArea(event: Event, areaId: string): Ticket[] {
    return event.tickets.filter((ticket) => ticket.areaId === areaId);
}

function getAvailableInArea(event: Event, areaId: string): number {
    return getTicketsByArea(event, areaId).filter(
        (ticket) => ticket.status === "AVAILABLE",
    ).length;
}

// Lone-seat heuristic per the LoneSeatRule policy:
// after the user's selection takes effect, an AVAILABLE unselected seat is
// "lone" if both its row neighbours are not AVAILABLE (or do not exist).
// This is intentionally a warning only — Phase 1 confirmation said do not
// block the purchase on this rule.
function detectLoneSeats(
    selection: SeatSelection,
    event: Event,
): LoneSeatLocation[] {
    const lone: LoneSeatLocation[] = [];
    const selectedSittingIds = new Set(getSelectedTicketIds(selection));

    for (const area of event.layout.areas) {
        if (area.kind !== "SITTING") {
            continue;
        }

        const ticketsByRow = new Map<number, Ticket[]>();
        for (const ticket of getTicketsByArea(event, area.id)) {
            if (ticket.row === undefined) {
                continue;
            }
            const existing = ticketsByRow.get(ticket.row) ?? [];
            existing.push(ticket);
            ticketsByRow.set(ticket.row, existing);
        }

        for (const [row, rowTickets] of ticketsByRow) {
            const sorted = [...rowTickets].sort(
                (a, b) => (a.seat ?? 0) - (b.seat ?? 0),
            );

            for (let index = 0; index < sorted.length; index += 1) {
                const ticket = sorted[index];
                if (ticket.status !== "AVAILABLE") {
                    continue;
                }
                if (selectedSittingIds.has(ticket.id)) {
                    continue;
                }

                const left = index > 0 ? sorted[index - 1] : null;
                const right = index < sorted.length - 1 ? sorted[index + 1] : null;

                const leftBlocked =
                    !left ||
                    left.status !== "AVAILABLE" ||
                    selectedSittingIds.has(left.id);
                const rightBlocked =
                    !right ||
                    right.status !== "AVAILABLE" ||
                    selectedSittingIds.has(right.id);

                if (leftBlocked && rightBlocked) {
                    lone.push({
                        areaName: area.name,
                        row,
                        seat: ticket.seat ?? 0,
                    });
                }
            }
        }
    }

    return lone;
}

function validateSelection(
    selection: SeatSelection,
    event: Event,
    agreedToTerms: boolean,
): ValidationResult {
    const blockers: string[] = [];
    const warnings: string[] = [];
    const totalCount = getTotalSelectedCount(selection);
    const policy = event.purchasePolicy;

    if (totalCount === 0) {
        blockers.push("Select at least one ticket to continue.");
    } else {
        if (
            policy.minTicketsPerPurchase !== undefined &&
            totalCount < policy.minTicketsPerPurchase
        ) {
            blockers.push(
                `This event requires at least ${policy.minTicketsPerPurchase} ticket${
                    policy.minTicketsPerPurchase === 1 ? "" : "s"
                } per purchase.`,
            );
        }
        if (
            policy.maxTicketsPerPurchase !== undefined &&
            totalCount > policy.maxTicketsPerPurchase
        ) {
            blockers.push(
                `This event allows at most ${policy.maxTicketsPerPurchase} ticket${
                    policy.maxTicketsPerPurchase === 1 ? "" : "s"
                } per purchase.`,
            );
        }
    }

    if (!agreedToTerms) {
        blockers.push("You must agree to the terms and conditions.");
    }

    if (policy.allowLoneSeat === false) {
        const lone = detectLoneSeats(selection, event);
        if (lone.length > 0) {
            const examples = lone
                .slice(0, 3)
                .map((entry) => `${entry.areaName} R${entry.row}·S${entry.seat}`)
                .join(", ");
            const suffix = lone.length > 3 ? `, +${lone.length - 3} more` : "";
            warnings.push(
                `This selection would leave isolated seats behind (${examples}${suffix}). The venue prefers contiguous seating.`,
            );
        }
    }

    return { blockers, warnings };
}

function formatEventDateTime(date: string): string {
    const parsed = new Date(date);
    if (Number.isNaN(parsed.getTime())) {
        return "Invalid date";
    }
    return parsed.toLocaleString("he-IL", {
        dateStyle: "full",
        timeStyle: "short",
    });
}

// ---------------------------------------------------------------------------
// PurchaseTimer — visual countdown component.
// Accepts a `targetDate` prop so the parent can later swap the mock deadline
// for the real `reservationExpiresAt` returned by the backend, without any
// code changes inside this component.
// ---------------------------------------------------------------------------

type PurchaseTimerProps = {
    targetDate: Date;
    onExpire?: () => void;
};

function PurchaseTimer({ targetDate, onExpire }: PurchaseTimerProps) {
    const [now, setNow] = useState<number>(() => Date.now());
    const expireFiredRef = useRef(false);

    useEffect(() => {
        const intervalId = setInterval(() => {
            setNow(Date.now());
        }, 1000);
        return () => clearInterval(intervalId);
    }, []);

    const msRemaining = targetDate.getTime() - now;
    const hasExpired = msRemaining <= 0;
    const secondsRemaining = Math.max(0, Math.floor(msRemaining / 1000));
    const minutes = Math.floor(secondsRemaining / 60);
    const seconds = secondsRemaining % 60;

    useEffect(() => {
        if (hasExpired && !expireFiredRef.current) {
            expireFiredRef.current = true;
            onExpire?.();
        }
    }, [hasExpired, onExpire]);

    let tone: "normal" | "warning" | "critical" | "expired" = "normal";
    if (hasExpired) {
        tone = "expired";
    } else if (secondsRemaining < 60) {
        tone = "critical";
    } else if (secondsRemaining < 180) {
        tone = "warning";
    }

    return (
        <div
            className={`purchase-timer tone-${tone}`}
            role="timer"
            aria-live="polite"
        >
            <span className="purchase-timer-label">
                {hasExpired ? "Time expired" : "Time remaining to complete purchase"}
            </span>
            <span className="purchase-timer-value">
                {String(minutes).padStart(2, "0")}:{String(seconds).padStart(2, "0")}
            </span>
        </div>
    );
}

// ---------------------------------------------------------------------------
// Event summary header — basic event details.
// ---------------------------------------------------------------------------

function EventSummaryCard({ event }: { event: Event }) {
    return (
        <section className="event-summary-card">
            {event.layout.mapImage && (
                <img
                    className="venue-photo"
                    src={event.layout.mapImage}
                    alt={`${event.name} venue layout`}
                />
            )}
            <div className="event-summary-content">
                <div>
                    <h1>{event.name}</h1>
                    <p className="event-summary-subtitle">
                        {event.artist} · {event.type}
                    </p>
                </div>
                <dl className="event-summary-meta">
                    <div>
                        <dt>Date & time</dt>
                        <dd>{formatEventDateTime(event.date)}</dd>
                    </div>
                    <div>
                        <dt>Venue</dt>
                        <dd>{event.location}</dd>
                    </div>
                    <div>
                        <dt>Production</dt>
                        <dd>{event.companyName}</dd>
                    </div>
                </dl>
            </div>
        </section>
    );
}

// ---------------------------------------------------------------------------
// Schematic layout — STAGE label + per-area blocks + ENTRANCE markers.
// Sitting areas render first, standing areas after — per Phase 1 confirmation.
// ---------------------------------------------------------------------------

type SchematicLayoutProps = {
    event: Event;
    selection: SeatSelection;
    onToggleSitting: (ticketId: string) => void;
    onAdjustStanding: (areaId: string, delta: number) => void;
    isInteractive: boolean;
};

function SchematicLayout({
    event,
    selection,
    onToggleSitting,
    onAdjustStanding,
    isInteractive,
}: SchematicLayoutProps) {
    const sittingAreas = event.layout.areas.filter(
        (area) => area.kind === "SITTING",
    );
    const standingAreas = event.layout.areas.filter(
        (area) => area.kind === "STANDING",
    );

    return (
        <section className="ticket-schematic" aria-label="Venue map">
            <div className="schematic-stage" aria-hidden="true">
                <span>▼ STAGE ▼</span>
            </div>

            <div className="schematic-areas">
                {sittingAreas.map((area) => (
                    <SittingGrid
                        key={area.id}
                        area={area}
                        tickets={getTicketsByArea(event, area.id)}
                        selection={selection}
                        onToggle={onToggleSitting}
                        isInteractive={isInteractive}
                    />
                ))}
                {standingAreas.map((area) => (
                    <StandingPicker
                        key={area.id}
                        area={area}
                        availableCount={getAvailableInArea(event, area.id)}
                        selectedCount={selection.standingCountByArea[area.id] ?? 0}
                        onAdjust={onAdjustStanding}
                        isInteractive={isInteractive}
                    />
                ))}
            </div>

            <div className="schematic-entrances" aria-hidden="true">
                <span>⇦ Entrance</span>
                <span>Entrance ⇨</span>
            </div>
        </section>
    );
}

// ---------------------------------------------------------------------------
// SittingGrid — rows × seats per sitting area; each cell IS a backend ticket.
// ---------------------------------------------------------------------------

type SittingGridProps = {
    area: Area;
    tickets: Ticket[];
    selection: SeatSelection;
    onToggle: (ticketId: string) => void;
    isInteractive: boolean;
};

function SittingGrid({
    area,
    tickets,
    selection,
    onToggle,
    isInteractive,
}: SittingGridProps) {
    const rows = useMemo(() => {
        const byRow = new Map<number, Ticket[]>();
        for (const ticket of tickets) {
            if (ticket.row === undefined) {
                continue;
            }
            const existing = byRow.get(ticket.row) ?? [];
            existing.push(ticket);
            byRow.set(ticket.row, existing);
        }
        return [...byRow.entries()]
            .sort(([rowA], [rowB]) => rowA - rowB)
            .map(([row, rowTickets]) => ({
                row,
                tickets: [...rowTickets].sort(
                    (a, b) => (a.seat ?? 0) - (b.seat ?? 0),
                ),
            }));
    }, [tickets]);

    const availableCount = tickets.filter(
        (ticket) => ticket.status === "AVAILABLE",
    ).length;

    return (
        <div className="schematic-area schematic-area-sitting">
            <header className="schematic-area-header">
                <div>
                    <h3>{area.name}</h3>
                    <p className="schematic-area-meta">
                        Seating · {area.price} NIS · {availableCount} available
                    </p>
                </div>
                <span className="schematic-area-kind">Sitting</span>
            </header>

            <div className="seat-grid">
                {rows.map(({ row, tickets: rowTickets }) => (
                    <div key={row} className="seat-row">
                        <span className="seat-row-label">Row {row}</span>
                        <div className="seat-row-seats">
                            {rowTickets.map((ticket) => (
                                <SeatButton
                                    key={ticket.id}
                                    ticket={ticket}
                                    isSelected={
                                        !!selection.sittingTicketIds[ticket.id]
                                    }
                                    onToggle={onToggle}
                                    isInteractive={isInteractive}
                                />
                            ))}
                        </div>
                    </div>
                ))}
            </div>

            <SeatLegend />
        </div>
    );
}

type SeatButtonProps = {
    ticket: Ticket;
    isSelected: boolean;
    onToggle: (ticketId: string) => void;
    isInteractive: boolean;
};

function SeatButton({
    ticket,
    isSelected,
    onToggle,
    isInteractive,
}: SeatButtonProps) {
    const isAvailable = ticket.status === "AVAILABLE";
    const disabled = !isAvailable || !isInteractive;

    const className = [
        "seat-button",
        `seat-status-${ticket.status.toLowerCase()}`,
        isSelected ? "selected" : "",
    ]
        .filter(Boolean)
        .join(" ");

    const baseLabel = `Row ${ticket.row ?? "?"}, seat ${ticket.seat ?? "?"}, ${ticket.price} NIS`;
    const stateLabel = isSelected
        ? "selected"
        : ticket.status === "AVAILABLE"
            ? "available"
            : ticket.status === "RESERVED"
                ? "reserved by another user"
                : "sold";

    return (
        <button
            type="button"
            className={className}
            disabled={disabled}
            aria-pressed={isSelected}
            aria-label={`${baseLabel}, ${stateLabel}`}
            title={`Row ${ticket.row} · Seat ${ticket.seat} · ${ticket.price} NIS · ${stateLabel}`}
            onClick={() => onToggle(ticket.id)}
        >
            {ticket.seat}
        </button>
    );
}

function SeatLegend() {
    return (
        <ul className="seat-legend" aria-label="Seat legend">
            <li>
                <span className="seat-legend-dot seat-status-available" /> Available
            </li>
            <li>
                <span className="seat-legend-dot seat-status-available selected" />{" "}
                Selected
            </li>
            <li>
                <span className="seat-legend-dot seat-status-reserved" /> Reserved
            </li>
            <li>
                <span className="seat-legend-dot seat-status-sold" /> Sold
            </li>
        </ul>
    );
}

// ---------------------------------------------------------------------------
// StandingPicker — count picker for standing areas.
// ---------------------------------------------------------------------------

type StandingPickerProps = {
    area: Area;
    availableCount: number;
    selectedCount: number;
    onAdjust: (areaId: string, delta: number) => void;
    isInteractive: boolean;
};

function StandingPicker({
    area,
    availableCount,
    selectedCount,
    onAdjust,
    isInteractive,
}: StandingPickerProps) {
    const canIncrease = isInteractive && selectedCount < availableCount;
    const canDecrease = isInteractive && selectedCount > 0;

    return (
        <div className="schematic-area schematic-area-standing">
            <header className="schematic-area-header">
                <div>
                    <h3>{area.name}</h3>
                    <p className="schematic-area-meta">
                        Standing · {area.price} NIS · {availableCount} available
                    </p>
                </div>
                <span className="schematic-area-kind">Standing</span>
            </header>

            <div className="standing-counter">
                <button
                    type="button"
                    className="standing-button"
                    disabled={!canDecrease}
                    aria-label={`Remove one ticket from ${area.name}`}
                    onClick={() => onAdjust(area.id, -1)}
                >
                    −
                </button>
                <span className="standing-count" aria-live="polite">
                    {selectedCount}
                </span>
                <button
                    type="button"
                    className="standing-button"
                    disabled={!canIncrease}
                    aria-label={`Add one ticket to ${area.name}`}
                    onClick={() => onAdjust(area.id, +1)}
                >
                    +
                </button>
                <span className="standing-subtotal">
                    {selectedCount > 0
                        ? `${selectedCount * area.price} NIS`
                        : "—"}
                </span>
            </div>
        </div>
    );
}

// ---------------------------------------------------------------------------
// PurchaseSummary — sticky right-hand panel: cart, totals, policy messages,
// terms checkbox, primary CTA, and the optional lottery off-ramp.
// ---------------------------------------------------------------------------

type SittingSummaryItem = {
    area: Area;
    tickets: Ticket[];
};

type StandingSummaryItem = {
    area: Area;
    count: number;
};

type PurchaseSummaryProps = {
    event: Event;
    selection: SeatSelection;
    blockers: string[];
    warnings: string[];
    mode: PurchaseStep;
    agreedToTerms: boolean;
    onToggleTerms: (next: boolean) => void;
    onContinueToCheckout: () => void;
    onJoinLottery: () => void;
    onConfirmPayment: () => void;
    onChangeSeats: () => void;
    onCancelReservation: () => void;
    isPerformingAction: boolean;
    isPaymentComplete: boolean;
    actionMessage: ActionResultMessage | null;
};

function PurchaseSummary({
    event,
    selection,
    blockers,
    warnings,
    mode,
    agreedToTerms,
    onToggleTerms,
    onContinueToCheckout,
    onJoinLottery,
    onConfirmPayment,
    onChangeSeats,
    onCancelReservation,
    isPerformingAction,
    isPaymentComplete,
    actionMessage,
}: PurchaseSummaryProps) {
    const totalCount = getTotalSelectedCount(selection);
    const totalPrice = getTotalPrice(selection, event);

    const sittingItems = useMemo<SittingSummaryItem[]>(() => {
        const ticketsById = new Map(
            event.tickets.map((ticket) => [ticket.id, ticket]),
        );
        const areasById = new Map(
            event.layout.areas.map((area) => [area.id, area]),
        );

        const byArea = new Map<string, Ticket[]>();
        for (const ticketId of getSelectedTicketIds(selection)) {
            const ticket = ticketsById.get(ticketId);
            if (!ticket) {
                continue;
            }
            const existing = byArea.get(ticket.areaId) ?? [];
            existing.push(ticket);
            byArea.set(ticket.areaId, existing);
        }

        const items: SittingSummaryItem[] = [];
        for (const [areaId, tickets] of byArea) {
            const area = areasById.get(areaId);
            if (!area) {
                continue;
            }
            items.push({
                area,
                tickets: [...tickets].sort(
                    (a, b) =>
                        (a.row ?? 0) - (b.row ?? 0) || (a.seat ?? 0) - (b.seat ?? 0),
                ),
            });
        }
        return items;
    }, [selection, event]);

    const standingItems = useMemo<StandingSummaryItem[]>(() => {
        const areasById = new Map(
            event.layout.areas.map((area) => [area.id, area]),
        );
        const items: StandingSummaryItem[] = [];
        for (const [areaId, count] of Object.entries(
            selection.standingCountByArea,
        )) {
            const area = areasById.get(areaId);
            if (!area || count <= 0) {
                continue;
            }
            items.push({ area, count });
        }
        return items;
    }, [selection, event]);

    const isSelectMode = mode === "select";
    // In checkout mode the cart is locked, so only show validation in
    // select mode where it can still drive the primary button state.
    const visibleWarnings = isSelectMode ? warnings : [];
    const visibleBlockers = isSelectMode ? blockers : [];
    const isPrimaryDisabled =
        isPerformingAction ||
        (isSelectMode
            ? blockers.length > 0
            : isPaymentComplete);

    return (
        <aside className="purchase-summary">
            <h2>{isSelectMode ? "Your tickets" : "Order summary"}</h2>

            {totalCount === 0 ? (
                <p className="purchase-summary-empty">
                    No tickets selected yet. Pick seats from the map or add
                    standing tickets to get started.
                </p>
            ) : (
                <ul className="purchase-summary-items">
                    {sittingItems.map((item) => (
                        <li
                            key={item.area.id}
                            className="purchase-summary-item"
                        >
                            <div>
                                <strong>{item.area.name}</strong>
                                <p className="purchase-summary-meta">
                                    {item.tickets
                                        .map(
                                            (ticket) =>
                                                `R${ticket.row}·S${ticket.seat}`,
                                        )
                                        .join(", ")}
                                </p>
                            </div>
                            <div className="purchase-summary-amount">
                                <span>
                                    {item.tickets.length} × {item.area.price}
                                </span>
                                <strong>
                                    {item.tickets.reduce(
                                        (sum, ticket) => sum + ticket.price,
                                        0,
                                    )}{" "}
                                    NIS
                                </strong>
                            </div>
                        </li>
                    ))}
                    {standingItems.map((item) => (
                        <li
                            key={item.area.id}
                            className="purchase-summary-item"
                        >
                            <div>
                                <strong>{item.area.name}</strong>
                                <p className="purchase-summary-meta">
                                    Standing · general admission
                                </p>
                            </div>
                            <div className="purchase-summary-amount">
                                <span>
                                    {item.count} × {item.area.price}
                                </span>
                                <strong>
                                    {item.count * item.area.price} NIS
                                </strong>
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            {totalCount > 0 && (
                <div className="purchase-summary-total">
                    <span>
                        Total ({totalCount} ticket{totalCount === 1 ? "" : "s"})
                    </span>
                    <strong>{totalPrice} NIS</strong>
                </div>
            )}

            {visibleWarnings.map((text) => (
                <div
                    key={text}
                    className="purchase-policy-message tone-warning"
                    role="status"
                >
                    {text}
                </div>
            ))}

            {visibleBlockers.map((text) => (
                <div
                    key={text}
                    className="purchase-policy-message tone-error"
                    role="alert"
                >
                    {text}
                </div>
            ))}

            {isSelectMode && (
                <label className="purchase-terms">
                    <input
                        type="checkbox"
                        checked={agreedToTerms}
                        onChange={(event_) =>
                            onToggleTerms(event_.target.checked)
                        }
                    />
                    <span>
                        I agree to the venue's terms and conditions and the
                        event's purchase policy.
                    </span>
                </label>
            )}

            {actionMessage && (
                <div
                    className={`purchase-action-message tone-${actionMessage.kind}`}
                    role={actionMessage.kind === "error" ? "alert" : "status"}
                >
                    {actionMessage.text}
                </div>
            )}

            {isSelectMode ? (
                <>
                    <button
                        type="button"
                        className="purchase-action-primary"
                        disabled={isPrimaryDisabled}
                        onClick={onContinueToCheckout}
                    >
                        {isPerformingAction
                            ? "Reserving seats…"
                            : totalCount > 0
                                ? `Continue to checkout · ${totalPrice} NIS`
                                : "Continue to checkout"}
                    </button>

                    {event.lotteryId && (
                        <button
                            type="button"
                            className="purchase-action-secondary"
                            onClick={onJoinLottery}
                            disabled={isPerformingAction}
                        >
                            Or join the lottery instead
                        </button>
                    )}
                </>
            ) : (
                <>
                    {!isPaymentComplete && (
                        <button
                            type="button"
                            className="purchase-action-primary"
                            disabled={isPrimaryDisabled}
                            onClick={onConfirmPayment}
                        >
                            {isPerformingAction
                                ? "Processing payment…"
                                : `Confirm payment · ${totalPrice} NIS`}
                        </button>
                    )}

                    {!isPaymentComplete && (
                        <div className="purchase-checkout-secondary-actions">
                            <button
                                type="button"
                                className="purchase-action-secondary"
                                onClick={onChangeSeats}
                                disabled={isPerformingAction}
                            >
                                ← Change seats
                            </button>
                            <button
                                type="button"
                                className="purchase-action-secondary purchase-action-danger"
                                onClick={onCancelReservation}
                                disabled={isPerformingAction}
                            >
                                Cancel reservation
                            </button>
                        </div>
                    )}
                </>
            )}
        </aside>
    );
}

type PaymentDetailsCardProps = {
    isPaymentComplete: boolean;
};

// Mock payment form for the checkout step. Disabled inputs are deliberate —
// when the API arrives we'll wire this to a real payment provider (Stripe
// Elements or similar) and feed the resulting tokenized payment details to
// PurchaseService.completePurchase.
function PaymentDetailsCard({ isPaymentComplete }: PaymentDetailsCardProps) {
    return (
        <section className="payment-details-card">
            <header className="payment-details-header">
                <h2>Payment details</h2>
                <p className="payment-details-subtitle">
                    {isPaymentComplete
                        ? "Payment received — your tickets are confirmed. Thanks for booking with us."
                        : "Your seats are held for the next 10 minutes. Enter your payment details and confirm to complete the purchase."}
                </p>
            </header>

            {!isPaymentComplete && (
                <form
                    className="payment-form"
                    onSubmit={(event_) => event_.preventDefault()}
                >
                    <label className="payment-field">
                        <span>Card number</span>
                        <input
                            type="text"
                            placeholder="•••• •••• •••• ••••"
                            disabled
                        />
                    </label>
                    <div className="payment-field-row">
                        <label className="payment-field">
                            <span>Expiry</span>
                            <input type="text" placeholder="MM/YY" disabled />
                        </label>
                        <label className="payment-field">
                            <span>CVV</span>
                            <input type="text" placeholder="•••" disabled />
                        </label>
                    </div>
                    <label className="payment-field">
                        <span>Cardholder name</span>
                        <input
                            type="text"
                            placeholder="As shown on card"
                            disabled
                        />
                    </label>
                    <p className="payment-form-notice">
                        Payment form is a mock for this milestone. Click
                        "Confirm payment" to simulate a successful purchase.
                    </p>
                </form>
            )}
        </section>
    );
}

// ---------------------------------------------------------------------------
// Main page — orchestrates state, async load, and the mock submit handlers.
// ---------------------------------------------------------------------------

export default function TicketPurchasePage({
    eventId,
    onBackToEvent,
}: TicketPurchasePageProps) {
    const [event, setEvent] = useState<Event | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState("");
    const [selection, setSelection] = useState<SeatSelection>(EMPTY_SELECTION);
    const [agreedToTerms, setAgreedToTerms] = useState(false);
    const [step, setStep] = useState<PurchaseStep>("select");
    const [isPaymentComplete, setIsPaymentComplete] = useState(false);
    const [isPerformingAction, setIsPerformingAction] = useState(false);
    const [actionMessage, setActionMessage] =
        useState<ActionResultMessage | null>(null);
    const [activePurchaseId, setActivePurchaseId] = useState<string | null>(null);
    const [showQueueMessage, setShowQueueMessage] = useState(false);
    const [queueMessage, setQueueMessage] = useState("");

    // Pinned the moment the user commits the cart (clicks "Continue to
    // checkout"). Null while they are still browsing — no lock, no timer.
    // When the API exists this becomes reservationExpiresAt returned by
    // POST /events/{id}/reservations (i.e. ActivePurchase.endTime). The
    // backend pins endTime once and does NOT refresh it on subsequent
    // updateActivePurchase…Tickets calls, so "Change seats" preserves it.
    const [reservationDeadline, setReservationDeadline] =
        useState<Date | null>(null);

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
                    setEvent(null);
                    setErrorMessage("This event could not be found.");
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

    function handleToggleSitting(ticketId: string) {
        setActionMessage(null);
        setSelection((previous) => {
            const nextSittingIds = { ...previous.sittingTicketIds };
            if (nextSittingIds[ticketId]) {
                delete nextSittingIds[ticketId];
            } else {
                nextSittingIds[ticketId] = true;
            }
            return { ...previous, sittingTicketIds: nextSittingIds };
        });
    }

    function handleAdjustStanding(areaId: string, delta: number) {
        if (!event) {
            return;
        }
        const available = getAvailableInArea(event, areaId);

        setActionMessage(null);
        setSelection((previous) => {
            const current = previous.standingCountByArea[areaId] ?? 0;
            const next = Math.max(0, Math.min(available, current + delta));
            const nextCounts = { ...previous.standingCountByArea };
            if (next === 0) {
                delete nextCounts[areaId];
            } else {
                nextCounts[areaId] = next;
            }
            return { ...previous, standingCountByArea: nextCounts };
        });
    }

    async function handleContinueToCheckout() {
        if (!event) {
            return;
        }

        setIsPerformingAction(true);
        setActionMessage(null);
        setShowQueueMessage(false);
        setQueueMessage("");

        try {
            const currentUser = await getCurrentUser();

            if (!currentUser) {
                setActionMessage({
                    kind: "error",
                    text: "You must be logged in to reserve tickets.",
                });
                return;
            }

            const sittingTicketIds = getSelectedTicketIds(selection);
            const standingEntries = Object.entries(selection.standingCountByArea)
                .filter(([, amount]) => amount > 0);

            let activePurchase: ActivePurchaseDTO | null = null;

            if (sittingTicketIds.length > 0) {
                activePurchase = await selectSittingTickets(
                    eventId,
                    sittingTicketIds,
                    currentUser.id,
                    true,
                );
            } else if (standingEntries.length > 0) {
                const [areaId, amount] = standingEntries[0];

                activePurchase = await selectStandingTickets(
                    eventId,
                    areaId,
                    amount,
                    currentUser.id,
                    true,
                );
            } else {
                setActionMessage({
                    kind: "error",
                    text: "Select at least one ticket to continue.",
                });
                return;
            }

            setActivePurchaseId(activePurchase.activePurchaseId);

            setReservationDeadline(
                activePurchase.endTime
                    ? new Date(activePurchase.endTime)
                    : new Date(Date.now() + PURCHASE_WINDOW_MINUTES * 60 * 1000),
            );

            setStep("checkout");
        } catch (error: any) {
            const message = error.response?.data?.message ?? "";

            if (message.includes("User is waiting in queue")) {
                setQueueMessage(message);
                setShowQueueMessage(true);
                setActionMessage({
                    kind: "error",
                    text: message,
                });
                return;
            }

            setActionMessage({
                kind: "error",
                text: message || "Failed to reserve tickets.",
            });
        } finally {
            setIsPerformingAction(false);
        }
    }

    function handleChangeSeats() {
        // Backend mirror: PurchaseService.updateActivePurchaseSittingTickets
        // / updateActivePurchaseStandingTickets release old tickets, reserve
        // new ones, and keep ActivePurchase.endTime pinned. The deadline
        // therefore continues to tick during this round-trip.
        setStep("select");
        setActionMessage(null);
    }

    function handleCancelReservation() {
        // Backend mirror: PurchaseService.cancelActivePurchase releases all
        // reserved tickets back to AVAILABLE and deletes the ActivePurchase.
        setStep("select");
        setReservationDeadline(null);
        setSelection(EMPTY_SELECTION);
        setAgreedToTerms(false);
        setActionMessage({
            kind: "success",
            text: "Reservation cancelled. Your seats have been released.",
        });
    }

    function handleTimerExpire() {
        // Backend mirror: ActivePurchaseCleaner sweeps any ActivePurchase
        // whose endTime has passed and releases its tickets.
        setStep("select");
        setReservationDeadline(null);
        setSelection(EMPTY_SELECTION);
        setAgreedToTerms(false);
        setActionMessage({
            kind: "error",
            text: "Your 10-minute reservation has expired. The seats have been released — please pick again.",
        });
    }

    function handleConfirmPayment() {
        if (!event) {
            return;
        }
        // TODO: Replace with PurchaseService.completePurchase(
        //   activePurchaseId, paymentDetails, couponCode
        // ). On success the backend flips RESERVED -> SOLD and deletes the
        // ActivePurchase.
        setIsPerformingAction(true);
        setActionMessage(null);
        setTimeout(() => {
            setIsPerformingAction(false);
            const total = getTotalPrice(selection, event);
            const count = getTotalSelectedCount(selection);
            setIsPaymentComplete(true);
            setReservationDeadline(null);
            setActionMessage({
                kind: "success",
                text: `Payment confirmed for ${count} ticket${count === 1 ? "" : "s"} (${total} NIS). Your tickets are on the way.`,
            });
        }, MOCK_RESERVATION_DELAY_MS);
    }

    function handleJoinLottery() {
        // TODO: Replace with POST /lotteries/{lotteryId}/registrations.
        setIsPerformingAction(true);
        setActionMessage(null);
        setTimeout(() => {
            setIsPerformingAction(false);
            setActionMessage({
                kind: "success",
                text: "You have been entered into the lottery. We'll notify you when results are drawn.",
            });
        }, MOCK_RESERVATION_DELAY_MS);
    }

    if (isLoading) {
        return (
            <main className="app-page ticket-purchase-page">
                <section className="empty-state">
                    <h2>Loading event…</h2>
                    <p>Please wait while we load the event details.</p>
                </section>
            </main>
        );
    }

    if (errorMessage || !event) {
        return (
            <main className="app-page ticket-purchase-page">
                <section className="empty-state">
                    <h2>Event unavailable</h2>
                    <p>{errorMessage || "This event could not be found."}</p>
                    <button
                        type="button"
                        className="purchase-action-secondary"
                        onClick={onBackToEvent}
                    >
                        Back to event details
                    </button>
                </section>
            </main>
        );
    }

    const bookable = isEventBookable(event);
    const validation = bookable
        ? validateSelection(selection, event, agreedToTerms)
        : { blockers: [], warnings: [] };
    // Seats are only interactive while the user is actively choosing them
    // (select step) and not waiting on an in-flight mock network call.
    const isInteractive =
        bookable && step === "select" && !isPerformingAction;

    return (
        <main className="app-page ticket-purchase-page">
            <button
                type="button"
                className="event-back-link"
                onClick={onBackToEvent}
            >
                ← Back to event details
            </button>

            <EventSummaryCard event={event} />

            {showQueueMessage && (
                <section className="purchase-action-message tone-warning" role="status">
                    <h2>You are waiting in queue</h2>
                    <p>{queueMessage}</p>
                    <p>You will be notified when your turn arrives.</p>
                </section>
            )}

            {/* The timer is the visible counterpart of the backend lock. It
                only renders once a reservation exists (deadline pinned) and
                hides again once the payment has settled. */}
            {bookable && reservationDeadline && !isPaymentComplete && (
                <PurchaseTimer
                    targetDate={reservationDeadline}
                    onExpire={handleTimerExpire}
                />
            )}

            {!bookable && (
                <section className="event-closed-banner" role="status">
                    <h2>This event is not available for purchase</h2>
                    <p>
                        {event.status === "CANCELED"
                            ? "This event has been cancelled. New purchases cannot be made."
                            : "This event has already ended. New purchases cannot be made."}
                    </p>
                </section>
            )}

            {bookable && (
                <div className="ticket-purchase-grid">
                    {step === "select" ? (
                        <SchematicLayout
                            event={event}
                            selection={selection}
                            onToggleSitting={handleToggleSitting}
                            onAdjustStanding={handleAdjustStanding}
                            isInteractive={isInteractive}
                        />
                    ) : (
                        <PaymentDetailsCard
                            isPaymentComplete={isPaymentComplete}
                        />
                    )}

                    <PurchaseSummary
                        event={event}
                        selection={selection}
                        blockers={validation.blockers}
                        warnings={validation.warnings}
                        mode={step}
                        agreedToTerms={agreedToTerms}
                        onToggleTerms={setAgreedToTerms}
                        onContinueToCheckout={handleContinueToCheckout}
                        onJoinLottery={handleJoinLottery}
                        onConfirmPayment={handleConfirmPayment}
                        onChangeSeats={handleChangeSeats}
                        onCancelReservation={handleCancelReservation}
                        isPerformingAction={isPerformingAction}
                        isPaymentComplete={isPaymentComplete}
                        actionMessage={actionMessage}
                    />
                </div>
            )}
        </main>
    );
}
