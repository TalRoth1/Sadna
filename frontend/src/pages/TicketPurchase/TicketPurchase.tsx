import { useEffect, useMemo, useRef, useState } from "react";
import { getEventById } from "../../services/eventSearchService";

import {
    ensureGuestSession,
    getStoredUserId,
    isLoggedInMember,
    isSessionInvalidError,
} from "../../services/authService";
import {
    cancelActivePurchase,
    completePurchase,
    getActivePurchaseForEvent,
    registerToLottery,
    selectSittingTickets,
    selectStandingTickets,
    updateSittingTickets,
    updateStandingTickets,
    type ActivePurchaseResponse,
} from "../../services/purchaseService";
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

// Pattern B: the user browses freely in "select" mode (no backend lock,
// no timer) and only commits to a reservation when they click "Continue
// to checkout". That commit is what mints the reservationDeadline and
// flips us into "checkout" mode — mirroring how the backend's
// PurchaseService.select{Sitting,Standing}Tickets creates the
// ActivePurchase with a fixed endTime.
type PurchaseStep = "select" | "checkout";

// The backend's purchase API offers two select endpoints — sitting (by
// ticket ids) and standing (single area + amount) — and the domain
// invariant "one active purchase per (user, event)" means we can have at
// most one shape live at a time. The UI mirrors that on the client.
type ActiveShape =
    | { kind: "sitting"; ticketIds: string[] }
    | { kind: "standing"; areaId: string; amount: number };

type ActivePurchaseState = {
    activePurchaseId: string;
    endTime: Date;
    shape: ActiveShape;
};

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

/**
 * Map the current cart to one of the two shapes the backend can reserve:
 *   - sitting: a flat list of ticket ids (any number of sitting areas)
 *   - standing: a single area + amount
 * Returns null if the cart mixes sitting and standing, or spreads
 * standing across more than one area — both of which violate the
 * "one active purchase per (user, event)" domain invariant and would
 * be impossible to send via select{Sitting,Standing}Tickets.
 */
function deriveActiveShape(selection: SeatSelection): ActiveShape | null {
    const sittingIds = getSelectedTicketIds(selection);
    const standingEntries = Object.entries(selection.standingCountByArea).filter(
        ([, count]) => count > 0,
    );

    if (sittingIds.length > 0 && standingEntries.length === 0) {
        return { kind: "sitting", ticketIds: sittingIds };
    }
    if (sittingIds.length === 0 && standingEntries.length === 1) {
        const [areaId, amount] = standingEntries[0];
        return { kind: "standing", areaId, amount };
    }
    return null;
}

/**
 * Rebuild the in-page selection + shape from an ActivePurchaseResponse, so
 * the resume flow can drop the user back into the exact state they left.
 *
 *   - Sitting tickets are recognised by `row`/`seat` being present in the
 *     event's ticket pool (matches eventSearchService.toTicket).
 *   - Standing tickets land in the same pool but with `row`/`seat` absent;
 *     the domain invariant guarantees they all share one area, so we
 *     count entries by areaId.
 *
 * Returns null when none of the reserved ticket ids can be located in the
 * event (e.g. a stale reservation against an event whose tickets were
 * regenerated) — the caller treats that as "don't resume, show a clean
 * slate".
 */
function reconstructFromActivePurchase(
    response: ActivePurchaseResponse,
    event: Event,
): { selection: SeatSelection; shape: ActiveShape } | null {
    const sittingTicketIds: Record<string, true> = {};
    const standingCountByArea: Record<string, number> = {};
    const orderedSittingIds: string[] = [];
    let standingAreaId: string | null = null;

    for (const ticketId of Object.keys(response.ticketPrices)) {
        const ticket = event.tickets.find((t) => t.id === ticketId);
        if (!ticket) {
            continue;
        }
        if (ticket.row !== undefined && ticket.seat !== undefined) {
            sittingTicketIds[ticketId] = true;
            orderedSittingIds.push(ticketId);
        } else {
            standingCountByArea[ticket.areaId] =
                (standingCountByArea[ticket.areaId] ?? 0) + 1;
            standingAreaId = ticket.areaId;
        }
    }

    const selection: SeatSelection = { sittingTicketIds, standingCountByArea };
    const standingAreaCount = Object.keys(standingCountByArea).length;

    if (orderedSittingIds.length > 0 && standingAreaCount === 0) {
        return {
            selection,
            shape: { kind: "sitting", ticketIds: orderedSittingIds },
        };
    }
    if (
        orderedSittingIds.length === 0 &&
        standingAreaCount === 1 &&
        standingAreaId
    ) {
        return {
            selection,
            shape: {
                kind: "standing",
                areaId: standingAreaId,
                amount: standingCountByArea[standingAreaId],
            },
        };
    }
    return null;
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
        // Single-shape constraint — the backend select endpoints accept
        // either a list of sitting ticket ids OR one standing area at a
        // time. Mixing is impossible per the domain invariant.
        if (totalCount > 0 && deriveActiveShape(selection) === null) {
            blockers.push(
                "Please pick either sitting tickets or standing tickets in a single area — not both.",
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
    onJoinLottery: (ticketAmount: number) => void;
    onConfirmPayment: () => void;
    onChangeSeats: () => void;
    onCancelReservation: () => void;
    isPerformingAction: boolean;
    isPaymentComplete: boolean;
    actionMessage: ActionResultMessage | null;
    isLotteryAvailable: boolean;
    canEnterLottery: boolean;
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
    isLotteryAvailable,
    canEnterLottery,
}: PurchaseSummaryProps) {
    const totalCount = getTotalSelectedCount(selection);
    const totalPrice = getTotalPrice(selection, event);
    const [lotteryTicketAmount, setLotteryTicketAmount] = useState<number>(1);

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

                    {isLotteryAvailable && (
                        <div className="purchase-lottery">
                            {canEnterLottery ? (
                                <>
                                    <label className="purchase-lottery-amount">
                                        <span>Tickets to enter the lottery for</span>
                                        <input
                                            type="number"
                                            min={1}
                                            value={lotteryTicketAmount}
                                            onChange={(event_) => {
                                                const parsed = Number(
                                                    event_.target.value,
                                                );
                                                setLotteryTicketAmount(
                                                    Number.isFinite(parsed) &&
                                                        parsed >= 1
                                                        ? Math.floor(parsed)
                                                        : 1,
                                                );
                                            }}
                                        />
                                    </label>
                                    <button
                                        type="button"
                                        className="purchase-action-secondary"
                                        onClick={() =>
                                            onJoinLottery(lotteryTicketAmount)
                                        }
                                        disabled={
                                            isPerformingAction ||
                                            lotteryTicketAmount < 1
                                        }
                                    >
                                        Or join the lottery instead
                                    </button>
                                </>
                            ) : (
                                <p className="purchase-lottery-locked">
                                    This event has a lottery. Log in as a
                                    member to enter it — guests can only buy
                                    standard tickets.
                                </p>
                            )}
                        </div>
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
    couponCode: string;
    onCouponCodeChange: (next: string) => void;
};

// Payment form for the checkout step. The card-detail inputs stay disabled
// on purpose — V2 of the assignment only requires calling an external
// payment service and surfacing its yes/no result, so the body POSTed to
// /complete is an empty PaymentDetails record. The one live input is the
// coupon code (Appendix §3c: "הרוכש מזין את הקוד במעמד התשלום").
function PaymentDetailsCard({
    isPaymentComplete,
    couponCode,
    onCouponCodeChange,
}: PaymentDetailsCardProps) {
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
                    <label className="payment-field">
                        <span>Coupon code (optional)</span>
                        <input
                            type="text"
                            placeholder="Enter a discount code"
                            value={couponCode}
                            onChange={(event_) =>
                                onCouponCodeChange(event_.target.value)
                            }
                            autoComplete="off"
                        />
                    </label>
                    <p className="payment-form-notice">
                        Card fields are illustrative — for this milestone the
                        backend's external payment gateway is contacted with
                        empty details and always approves. Coupon codes are
                        validated server-side at checkout.
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
    const [couponCode, setCouponCode] = useState<string>("");
    // Queue feature (from main): when the backend reports the user is
    // waiting in a virtual queue, we surface a dedicated banner instead
    // of the generic error toast.
    const [showQueueMessage, setShowQueueMessage] = useState(false);
    const [queueMessage, setQueueMessage] = useState("");

    // The server-issued active purchase. Holds the canonical endTime the
    // PurchaseTimer should track and the last-known reservation shape, so
    // we can pick between updateSitting/updateStanding (or fall back to a
    // cancel+select round-trip when the user changes shape entirely).
    const [activePurchase, setActivePurchase] =
        useState<ActivePurchaseState | null>(null);

    useEffect(() => {
        let isCancelled = false;

        // App.tsx remounts this component (via key={eventId}) when the
        // user navigates between events, so per-event state is fresh on
        // arrival — no manual resets needed here.
        async function loadEventAndResume() {
            try {
                setIsLoading(true);

                const result = await getEventById(eventId);
                if (isCancelled) {
                    return;
                }
                if (!result) {
                    setErrorMessage("This event could not be found.");
                    return;
                }
                setEvent(result);

                // Resume: if a guest/member session is cached, ask the
                // backend whether the user has an in-flight reservation
                // for THIS specific event. Per spec (general doc, page 2),
                // a user can have at most one active purchase per event,
                // so this query is unambiguous. The server keeps the
                // 10-minute timer running while the page was unmounted,
                // and ActivePurchaseCleaner sweeps it when it lapses —
                // in which case we get null here and act as a fresh page.
                const userId = getStoredUserId();
                if (!userId) {
                    return;
                }
                try {
                    const activeResponse = await getActivePurchaseForEvent(
                        eventId,
                        userId,
                    );
                    if (isCancelled || !activeResponse) {
                        return;
                    }
                    const restored = reconstructFromActivePurchase(
                        activeResponse,
                        result,
                    );
                    if (!restored) {
                        // The reservation still exists server-side but its
                        // ticket ids don't match anything we can render
                        // (e.g. event tickets were regenerated). Bail out
                        // of resume; the user can either wait for the
                        // server-side timer to expire or cancel manually
                        // through "My tickets" once that page lands.
                        return;
                    }
                    setSelection(restored.selection);
                    // The user already accepted terms when they made this
                    // reservation; the backend wouldn't have created it
                    // otherwise. Reinstating the checkbox keeps the
                    // "Confirm payment" button enabled.
                    setAgreedToTerms(true);
                    setStep("checkout");
                    setActivePurchase({
                        activePurchaseId: activeResponse.activePurchaseId,
                        endTime: new Date(activeResponse.endTime),
                        shape: restored.shape,
                    });
                } catch {
                    // Best-effort resume. A transient lookup failure must
                    // not block the read-only event view.
                }
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

        loadEventAndResume();
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

    // Translate a backend ActivePurchaseDTO into our local state. Pinning
    // the deadline to `endTime` (rather than computing it client-side) is
    // what keeps the timer in lockstep with ActivePurchaseCleaner on the
    // server — see PurchaseService.toActivePurchaseDTO.
    function adoptActivePurchase(
        response: ActivePurchaseResponse,
        shape: ActiveShape,
    ): ActivePurchaseState {
        const state: ActivePurchaseState = {
            activePurchaseId: response.activePurchaseId,
            endTime: new Date(response.endTime),
            shape,
        };
        setActivePurchase(state);
        return state;
    }

    // Issue the actual select* call against a known userId. Pulled out so
    // we can retry it with a fresh session if the first attempt fails
    // because the cached guest userId is no longer recognised by the
    // server (e.g. after a backend restart wiped the in-memory user repo).
    async function selectForShape(
        event_: Event,
        shape: ActiveShape,
        userId: string,
    ): Promise<ActivePurchaseResponse> {
        return shape.kind === "sitting"
            ? await selectSittingTickets(
                  event_.id,
                  shape.ticketIds,
                  userId,
                  agreedToTerms,
              )
            : await selectStandingTickets(
                  event_.id,
                  shape.areaId,
                  shape.amount,
                  userId,
                  agreedToTerms,
              );
    }

    // Run a select* call (cancelling any leftover reservation first, so we
    // honour "at most one active purchase per (user, event)") and stash
    // the resulting active purchase. Returns false on failure with the
    // backend message surfaced as actionMessage.
    async function createReservation(
        event_: Event,
        shape: ActiveShape,
    ): Promise<boolean> {
        try {
            if (activePurchase) {
                try {
                    await cancelActivePurchase(activePurchase.activePurchaseId);
                } catch {
                    // Best-effort: even if the old reservation has already
                    // expired or been swept, we still try to open a fresh
                    // one. Any persistent problem will resurface on the
                    // select* call below.
                }
                setActivePurchase(null);
            }

            let session = await ensureGuestSession();
            let response: ActivePurchaseResponse;
            try {
                response = await selectForShape(event_, shape, session.userId);
            } catch (error) {
                // Self-heal: if the backend says the cached guest is
                // stale, mint a fresh session and try exactly once more.
                const message =
                    error instanceof Error ? error.message : "";
                if (!isSessionInvalidError(message)) {
                    throw error;
                }
                session = await ensureGuestSession(true);
                response = await selectForShape(event_, shape, session.userId);
            }

            adoptActivePurchase(response, shape);
            return true;
        } catch (error) {
            setActionMessage({
                kind: "error",
                text:
                    error instanceof Error
                        ? error.message
                        : "Failed to reserve tickets.",
            });
            return false;
        }
    }

    async function handleContinueToCheckout() {
        if (!event) {
            return;
        }
        const shape = deriveActiveShape(selection);
        if (!shape) {
            return;
        }

        setIsPerformingAction(true);
        setActionMessage(null);
        // Queue feature: clear any banner from a previous attempt so a
        // successful retry doesn't leave stale UI behind.
        setShowQueueMessage(false);
        setQueueMessage("");

        try {
            if (!activePurchase) {
                const ok = await createReservation(event, shape);
                if (!ok) return;
            } else if (
                activePurchase.shape.kind === "sitting" &&
                shape.kind === "sitting"
            ) {
                await updateSittingTickets(
                    activePurchase.activePurchaseId,
                    shape.ticketIds,
                );
                setActivePurchase({ ...activePurchase, shape });
            } else if (
                activePurchase.shape.kind === "standing" &&
                shape.kind === "standing"
            ) {
                await updateStandingTickets(
                    activePurchase.activePurchaseId,
                    shape.areaId,
                    shape.amount,
                );
                setActivePurchase({ ...activePurchase, shape });
            } else {
                // Shape changed (sitting <-> standing). The domain forbids
                // mixing, so we cancel and open a fresh reservation — this
                // unavoidably restarts the 10-min window, which is the
                // correct semantic.
                const ok = await createReservation(event, shape);
                if (!ok) return;
            }

            setStep("checkout");
        } catch (error) {
            const message = error instanceof Error ? error.message : "";
            // Queue feature (from main): surface a dedicated banner when
            // the backend tells us the user is waiting in queue, so the
            // UI can match the rest of the queue UX.
            if (message.includes("User is waiting in queue")) {
                setQueueMessage(message);
                setShowQueueMessage(true);
            }
            setActionMessage({
                kind: "error",
                text: message || "Could not continue to checkout.",
            });
        } finally {
            setIsPerformingAction(false);
        }
    }

    function handleChangeSeats() {
        // The active purchase stays alive server-side with its original
        // endTime — the next "Continue to checkout" click will route to
        // updateSitting/updateStanding (or cancel+select on shape change).
        setStep("select");
        setActionMessage(null);
    }

    async function handleCancelReservation() {
        if (!activePurchase) {
            // Defensive: keep the UI consistent even if state slipped.
            setStep("select");
            setSelection(EMPTY_SELECTION);
            setAgreedToTerms(false);
            return;
        }

        setIsPerformingAction(true);
        setActionMessage(null);

        try {
            await cancelActivePurchase(activePurchase.activePurchaseId);
            setActivePurchase(null);
            setStep("select");
            setSelection(EMPTY_SELECTION);
            setAgreedToTerms(false);
            setCouponCode("");
            setActionMessage({
                kind: "success",
                text: "Reservation cancelled. Your seats have been released.",
            });
        } catch (error) {
            setActionMessage({
                kind: "error",
                text:
                    error instanceof Error
                        ? error.message
                        : "Could not cancel the reservation.",
            });
        } finally {
            setIsPerformingAction(false);
        }
    }

    function handleTimerExpire() {
        // Mirrors ActivePurchaseCleaner: the server has already released
        // the tickets, so we just reset the local view.
        setActivePurchase(null);
        setStep("select");
        setSelection(EMPTY_SELECTION);
        setAgreedToTerms(false);
        setCouponCode("");
        setActionMessage({
            kind: "error",
            text: "Your 10-minute reservation has expired. The seats have been released — please pick again.",
        });
    }

    async function handleConfirmPayment() {
        if (!event || !activePurchase) {
            return;
        }

        setIsPerformingAction(true);
        setActionMessage(null);

        try {
            const trimmedCoupon = couponCode.trim();
            await completePurchase(
                activePurchase.activePurchaseId,
                trimmedCoupon === "" ? null : trimmedCoupon,
            );
            const total = getTotalPrice(selection, event);
            const count = getTotalSelectedCount(selection);
            setIsPaymentComplete(true);
            setActivePurchase(null);
            setActionMessage({
                kind: "success",
                text: `Payment confirmed for ${count} ticket${count === 1 ? "" : "s"} (${total} NIS). Your tickets are on the way.`,
            });
        } catch (error) {
            setActionMessage({
                kind: "error",
                text:
                    error instanceof Error
                        ? error.message
                        : "Payment failed. Please try again.",
            });
        } finally {
            setIsPerformingAction(false);
        }
    }

    async function handleJoinLottery(ticketAmount: number) {
        if (!event || !event.lotteryId) {
            return;
        }
        const memberId = getStoredUserId();
        if (!memberId || !isLoggedInMember()) {
            setActionMessage({
                kind: "error",
                text: "Please log in as a member to enter the lottery.",
            });
            return;
        }
        if (!Number.isFinite(ticketAmount) || ticketAmount < 1) {
            setActionMessage({
                kind: "error",
                text: "Choose how many tickets you want to enter for (at least 1).",
            });
            return;
        }

        setIsPerformingAction(true);
        setActionMessage(null);

        try {
            await registerToLottery(event.id, memberId, Math.floor(ticketAmount));
            setActionMessage({
                kind: "success",
                text: "You have been entered into the lottery. We'll notify you if you're drawn.",
            });
        } catch (error) {
            setActionMessage({
                kind: "error",
                text:
                    error instanceof Error
                        ? error.message
                        : "Failed to register for the lottery.",
            });
        } finally {
            setIsPerformingAction(false);
        }
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
                only renders once a reservation exists (deadline pinned to
                ActivePurchase.endTime) and hides once payment has settled. */}
            {bookable && activePurchase && !isPaymentComplete && (
                <PurchaseTimer
                    targetDate={activePurchase.endTime}
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
                            couponCode={couponCode}
                            onCouponCodeChange={setCouponCode}
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
                        isLotteryAvailable={Boolean(event.lotteryId)}
                        canEnterLottery={isLoggedInMember()}
                    />
                </div>
            )}
        </main>
    );
}
