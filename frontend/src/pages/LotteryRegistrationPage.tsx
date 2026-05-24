import { useEffect, useState } from "react";
import { getEventById } from "../services/eventSearchService";
import { getCurrentUser } from "../services/currentUserService";
import { registerToLottery } from "../services/lotteryService";
import type { Event } from "../types/event";

type LotteryRegistrationPageProps = {
    eventId: string;
    onBackToEvent: () => void;
};

function formatEventDate(date: string) {
    const parsedDate = new Date(date);

    if (Number.isNaN(parsedDate.getTime())) {
        return "Invalid date";
    }

    return parsedDate.toLocaleString("he-IL", {
        dateStyle: "full",
        timeStyle: "short",
    });
}

function getErrorMessage(error: unknown) {
    if (
        typeof error === "object" &&
        error !== null &&
        "response" in error &&
        typeof error.response === "object" &&
        error.response !== null &&
        "data" in error.response &&
        typeof error.response.data === "object" &&
        error.response.data !== null &&
        "message" in error.response.data &&
        typeof error.response.data.message === "string"
    ) {
        return error.response.data.message;
    }

    if (error instanceof Error) {
        return error.message;
    }

    return "Failed to register to lottery.";
}

export default function LotteryRegistrationPage({
    eventId,
    onBackToEvent,
}: LotteryRegistrationPageProps) {
    const [event, setEvent] = useState<Event | null>(null);
    const [ticketAmount, setTicketAmount] = useState(1);
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isRegistered, setIsRegistered] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        async function loadEvent() {
            try {
                setIsLoading(true);
                setErrorMessage("");

                const result = await getEventById(eventId);
                setEvent(result);
            } catch {
                setErrorMessage("Failed to load event details.");
            } finally {
                setIsLoading(false);
            }
        }

        loadEvent();
    }, [eventId]);

    async function handleRegister() {
        if (!event) {
            return;
        }

        const minTickets = event.purchasePolicy.minTicketsPerPurchase ?? 1;
        const maxTickets = event.purchasePolicy.maxTicketsPerPurchase;

        if (ticketAmount < minTickets) {
            setErrorMessage(
                `This lottery requires at least ${minTickets} ticket${
                    minTickets === 1 ? "" : "s"
                }.`,
            );
            return;
        }

        if (maxTickets !== undefined && ticketAmount > maxTickets) {
            setErrorMessage(
                `This lottery allows at most ${maxTickets} ticket${
                    maxTickets === 1 ? "" : "s"
                }.`,
            );
            return;
        }

        try {
            setIsSubmitting(true);
            setErrorMessage("");

            const currentUser = await getCurrentUser();

            if (!currentUser) {
                setErrorMessage("You must be logged in as a member to register.");
                return;
            }

            await registerToLottery(eventId, currentUser.id, ticketAmount);

            setIsRegistered(true);
        } catch (error) {
            setErrorMessage(getErrorMessage(error));
        } finally {
            setIsSubmitting(false);
        }
    }

    if (isLoading) {
        return (
            <main className="app-page">
                <section className="empty-state">
                    <h2>Loading lottery registration...</h2>
                    <p>Please wait while we load the event.</p>
                </section>
            </main>
        );
    }

    if (!event) {
        return (
            <main className="app-page">
                <section className="empty-state">
                    <h2>Event not found</h2>
                    <p>{errorMessage || "Could not load this event."}</p>
                    <button type="button" onClick={onBackToEvent}>
                        Back to event
                    </button>
                </section>
            </main>
        );
    }

    const minTickets = event.purchasePolicy.minTicketsPerPurchase ?? 1;
    const maxTickets = event.purchasePolicy.maxTicketsPerPurchase;

    const isBelowMin = ticketAmount < minTickets;
    const isAboveMax = maxTickets !== undefined && ticketAmount > maxTickets;
    const isTicketAmountInvalid = isBelowMin || isAboveMax;

    const ticketAmountMessage = isBelowMin
        ? `Minimum tickets for this lottery: ${minTickets}.`
        : isAboveMax
          ? `Maximum tickets for this lottery: ${maxTickets}.`
          : maxTickets !== undefined
            ? `You can request between ${minTickets} and ${maxTickets} tickets.`
            : `You can request at least ${minTickets} ticket${
                  minTickets === 1 ? "" : "s"
              }.`;

    return (
        <main className="app-page">
            <section className="page-header">
                <h1>Lottery Registration</h1>
                <p>This registration gives you a chance to buy tickets later.</p>
            </section>

            <section className="event-details-card">
                <h2>{event.name}</h2>
                <p>{event.artist} · {event.type}</p>
                <p>{formatEventDate(event.date)}</p>
                <p>{event.location}</p>
            </section>

            <section className="auth-card">
                <h2>This is not an immediate purchase</h2>
                <p>
                    You are registering for a lottery. If you win, you will receive
                    an access code that allows you to continue to ticket selection.
                </p>

                <label className="form-field">
                    <span>Requested tickets</span>
                        <input
                            type="number"
                            min={minTickets}
                            max={maxTickets}
                            value={ticketAmount}
                            disabled={isSubmitting || isRegistered}
                            onChange={(e) => setTicketAmount(Number(e.target.value))}
                        />
                        <small>{ticketAmountMessage}</small>
                </label>

                <button
                    type="button"
                    onClick={handleRegister}
                    disabled={isSubmitting || isRegistered || isTicketAmountInvalid}
                >
                    {isSubmitting
                        ? "Registering..."
                        : isRegistered
                          ? "Registered"
                          : "Register to lottery"}
                </button>

                {isRegistered && (
                    <p className="success-message">
                        You have successfully registered to the lottery.
                    </p>
                )}

                {errorMessage && <p className="error-message">{errorMessage}</p>}

                <button type="button" onClick={onBackToEvent}>
                    Back to event details
                </button>
            </section>
        </main>
    );
}