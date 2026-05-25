import { useEffect, useMemo, useState, type FormEvent } from "react";
import { getEventById } from "../../services/eventSearchService";
import { getCurrentUser, type CurrentUser } from "../../services/currentUserService";
import { getMyCompanies, type CompanyMembership } from "../../services/companyService";
import type { Event, EventStatus } from "../../types/event";
import "../createEvent/CreateEventPage.css";
import {
    editEvent,
    editEventPolicy,
    type EditEventRequest,
} from "../../services/eventService";
type EditEventPageProps = {
    eventId: string;
    onBackToEvent: () => void;
    onLogin: () => void;
    onRegister: () => void;
    onEventUpdated?: (eventId: string) => void;
};

type FormErrors = {
    name?: string;
    date?: string;
    location?: string;
    type?: string;
};

const statusOptions: { value: EventStatus; label: string }[] = [
    { value: "ACTIVE", label: "Active" },
    { value: "ENDED", label: "Ended" },
    { value: "CANCELED", label: "Canceled" },
];

function toDateTimeLocalValue(date: string) {
    const parsed = new Date(date);

    if (Number.isNaN(parsed.getTime())) {
        return "";
    }

    const offsetMs = parsed.getTimezoneOffset() * 60 * 1000;
    return new Date(parsed.getTime() - offsetMs).toISOString().slice(0, 16);
}

function canUserEditEvent(
    event: Event | null,
    currentUser: CurrentUser | null,
    companies: CompanyMembership[],
) {
    if (!event || !currentUser) {
        return false;
    }

    if (currentUser.role === "ADMIN") {
        return true;
    }

    const companyMembership = companies.find(
        (company) => company.companyId === event.companyId,
    );

    if (!companyMembership) {
        return false;
    }

    const role = String((companyMembership as any).role ?? "").toLowerCase();
    const permissions = ((companyMembership as any).permissions ?? []) as string[];

    return (
        role === "founder" ||
        role === "owner" ||
        role === "manager" ||
        permissions.includes("Manage inventory") ||
        permissions.includes("Manage policies") ||
        permissions.includes("Configure layout")
    );
}

export default function EditEventPage({
    eventId,
    onBackToEvent,
    onLogin,
    onRegister,
    onEventUpdated,
}: EditEventPageProps) {
    const [event, setEvent] = useState<Event | null>(null);
    const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
    const [companies, setCompanies] = useState<CompanyMembership[]>([]);

    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [date, setDate] = useState("");
    const [location, setLocation] = useState("");
    const [artist, setArtist] = useState("");
    const [type, setType] = useState("");
    const [status, setStatus] = useState<EventStatus>("ACTIVE");

    const [minAge, setMinAge] = useState("");
    const [minTickets, setMinTickets] = useState("");
    const [maxTickets, setMaxTickets] = useState("");
    const [allowLoneSeat, setAllowLoneSeat] = useState("");

    const [errors, setErrors] = useState<FormErrors>({});
    const [successMessage, setSuccessMessage] = useState("");
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        let isCancelled = false;

        async function loadData() {
            try {
                setIsLoading(true);
                setErrorMessage("");

                const [loadedEvent, user] = await Promise.all([
                    getEventById(eventId),
                    getCurrentUser(),
                ]);

                if (isCancelled) {
                    return;
                }

                if (!loadedEvent) {
                    setErrorMessage("This event could not be found.");
                    setEvent(null);
                    return;
                }

                setEvent(loadedEvent);
                setCurrentUser(user);

                setName(loadedEvent.name);
                setDescription(loadedEvent.description ?? "");
                setDate(toDateTimeLocalValue(loadedEvent.date));
                setLocation(loadedEvent.location);
                setArtist(loadedEvent.artist);
                setType(loadedEvent.type);
                setStatus(loadedEvent.status);

                setMinAge(
                    loadedEvent.purchasePolicy.minAge !== undefined
                        ? String(loadedEvent.purchasePolicy.minAge)
                        : "",
                );
                setMinTickets(
                    loadedEvent.purchasePolicy.minTicketsPerPurchase !== undefined
                        ? String(loadedEvent.purchasePolicy.minTicketsPerPurchase)
                        : "",
                );
                setMaxTickets(
                    loadedEvent.purchasePolicy.maxTicketsPerPurchase !== undefined
                        ? String(loadedEvent.purchasePolicy.maxTicketsPerPurchase)
                        : "",
                );
                setAllowLoneSeat(
                    loadedEvent.purchasePolicy.allowLoneSeat === undefined
                        ? ""
                        : String(loadedEvent.purchasePolicy.allowLoneSeat),
                );

                if (!user || user.role === "GUEST") {
                    setCompanies([]);
                    return;
                }

                const userCompanies = await getMyCompanies(user.email);

                if (!isCancelled) {
                    setCompanies(userCompanies);
                }
            } catch {
                if (!isCancelled) {
                    setErrorMessage("Failed to load event edit page.");
                }
            } finally {
                if (!isCancelled) {
                    setIsLoading(false);
                }
            }
        }

        loadData();

        return () => {
            isCancelled = true;
        };
    }, [eventId]);

    const userCanEdit = useMemo(
        () => canUserEditEvent(event, currentUser, companies),
        [event, currentUser, companies],
    );

    const isGuest = currentUser === null || currentUser.role === "GUEST";

    function validateForm() {
        const validation: FormErrors = {};

        if (!name.trim()) validation.name = "Event name is required.";
        if (!date) validation.date = "Event date and time are required.";
        if (!location.trim()) validation.location = "Location is required.";
        if (!type.trim()) validation.type = "Category or event type is required.";

        return validation;
    }

    async function handleSubmit(submitEvent: FormEvent<HTMLFormElement>) {
        submitEvent.preventDefault();

        setSuccessMessage("");
        setErrorMessage("");

        if (!event) {
            setErrorMessage("Event could not be found.");
            return;
        }

        if (!currentUser?.email) {
            setErrorMessage("Could not identify the current user. Please log in again.");
            return;
        }

        if (!userCanEdit) {
            setErrorMessage("You do not have permission to edit this event.");
            return;
        }

        const validationErrors = validateForm();
        setErrors(validationErrors);

        if (Object.keys(validationErrors).length > 0) {
            setErrorMessage("Please fix the highlighted fields before submitting.");
            return;
        }

        try {
            setIsSubmitting(true);

            const request: EditEventRequest = {
                name: name.trim(),
                date: `${date}:00`,
                location: location.trim(),
                artist: artist.trim(),
                type: type.trim(),
                status,
                description: description.trim() || undefined,
            };

            await editEvent(event.id, request);

            await editEventPolicy(event.id, {
                username: currentUser.email,
                companyId: event.companyId,
                age: minAge.trim() ? Number(minAge) : null,
                minTicket: minTickets.trim() ? Number(minTickets) : null,
                maxTicket: maxTickets.trim() ? Number(maxTickets) : null,
                allowLoneSeat:
                    allowLoneSeat === ""
                        ? null
                        : allowLoneSeat === "true",
            });

            setSuccessMessage("Event updated successfully.");

            if (onEventUpdated) {
                onEventUpdated(event.id);
            }
        } catch (error) {
            setErrorMessage(
                error instanceof Error
                    ? error.message
                    : "Failed to update event. Please try again.",
            );
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <main className="app-page create-event-page">
            <section className="page-header">
                <button
                    type="button"
                    className="event-back-link"
                    onClick={onBackToEvent}
                >
                    ← Back to event
                </button>

                <h1>Edit Event</h1>
                <p>Update the event details using the same layout as event creation.</p>
            </section>

            {isLoading && (
                <section className="empty-state">
                    <h2>Loading...</h2>
                    <p>Please wait while we load the event and your permissions.</p>
                </section>
            )}

            {!isLoading && isGuest && (
                <section className="empty-state create-event-access-card">
                    <h2>Member access required</h2>
                    <p>You need to be logged in to edit an event.</p>
                    <div className="create-event-actions">
                        <button type="button" className="create-event-button" onClick={onLogin}>
                            Log in
                        </button>
                        <button
                            type="button"
                            className="create-event-button create-event-button--secondary"
                            onClick={onRegister}
                        >
                            Register
                        </button>
                    </div>
                </section>
            )}

            {!isLoading && !isGuest && !userCanEdit && (
                <section className="empty-state create-event-access-card">
                    <h2>No edit permission</h2>
                    <p>You do not have permission to edit this event.</p>
                    <button
                        type="button"
                        className="create-event-button"
                        onClick={onBackToEvent}
                    >
                        Back to event
                    </button>
                </section>
            )}

            {!isLoading && !isGuest && userCanEdit && event && (
                <section className="create-event-card">
                    <form className="create-event-form" onSubmit={handleSubmit} noValidate>
                        <div className="create-event-grid">
                            <section className="create-event-panel">
                                <h2>Event details</h2>

                                <label className="form-field">
                                    <span>Company</span>
                                    <input
                                        type="text"
                                        value={event.companyName}
                                        disabled
                                    />
                                </label>

                                <label className="form-field">
                                    <span>Event name</span>
                                    <input
                                        type="text"
                                        value={name}
                                        onChange={(e) => setName(e.target.value)}
                                        placeholder="Enter event name"
                                    />
                                    {errors.name && <small>{errors.name}</small>}
                                </label>

                                <label className="form-field">
                                    <span>Artist / performer</span>
                                    <input
                                        type="text"
                                        value={artist}
                                        onChange={(e) => setArtist(e.target.value)}
                                        placeholder="Artist or headline performer"
                                    />
                                </label>

                                <label className="form-field">
                                    <span>Category / event type</span>
                                    <input
                                        type="text"
                                        value={type}
                                        onChange={(e) => setType(e.target.value)}
                                        placeholder="Music, theatre, conference, etc."
                                    />
                                    {errors.type && <small>{errors.type}</small>}
                                </label>

                                <label className="form-field">
                                    <span>Location</span>
                                    <input
                                        type="text"
                                        value={location}
                                        onChange={(e) => setLocation(e.target.value)}
                                        placeholder="City, venue or region"
                                    />
                                    {errors.location && <small>{errors.location}</small>}
                                </label>

                                <label className="form-field">
                                    <span>Date and time</span>
                                    <input
                                        type="datetime-local"
                                        value={date}
                                        onChange={(e) => setDate(e.target.value)}
                                    />
                                    {errors.date && <small>{errors.date}</small>}
                                </label>

                                <label className="form-field">
                                    <span>Event status</span>
                                    <select
                                        value={status}
                                        onChange={(e) => setStatus(e.target.value as EventStatus)}
                                    >
                                        {statusOptions.map((option) => (
                                            <option key={option.value} value={option.value}>
                                                {option.label}
                                            </option>
                                        ))}
                                    </select>
                                </label>

                                <label className="form-field">
                                    <span>Event description</span>
                                    <textarea
                                        value={description}
                                        onChange={(e) => setDescription(e.target.value)}
                                        placeholder="Describe the event"
                                        rows={4}
                                    />
                                </label>
                            </section>

                            <section className="create-event-panel">
                                <h2>Ticket areas</h2>
                                <p className="form-note">
                                    Ticket area editing needs a backend endpoint for updating an existing area.
                                    For now, current prices and tickets are shown in the event details page.
                                </p>

                                <div className="create-event-feedback">
                                    <p className="create-event-error">
                                        Existing backend supports creating ticket areas, but not editing existing ticket area prices yet.
                                    </p>
                                </div>
                            </section>

                            <section className="create-event-panel">
                                <h2>Purchase policy</h2>
                                <p className="form-note">
                                    Updating this section currently adds a policy rule through the existing backend endpoint.
                                </p>

                                <label className="form-field">
                                    <span>Minimum age</span>
                                    <input
                                        type="number"
                                        min={0}
                                        value={minAge}
                                        onChange={(e) => setMinAge(e.target.value)}
                                        placeholder="e.g. 18"
                                    />
                                </label>

                                <label className="form-field">
                                    <span>Minimum tickets per purchase</span>
                                    <input
                                        type="number"
                                        min={0}
                                        step={1}
                                        value={minTickets}
                                        onChange={(e) => setMinTickets(e.target.value)}
                                        placeholder="e.g. 1"
                                    />
                                </label>

                                <label className="form-field">
                                    <span>Maximum tickets per purchase</span>
                                    <input
                                        type="number"
                                        min={0}
                                        step={1}
                                        value={maxTickets}
                                        onChange={(e) => setMaxTickets(e.target.value)}
                                        placeholder="e.g. 4"
                                    />
                                </label>

                                <label className="form-field">
                                    <span>Allow lone seat</span>
                                    <select
                                        value={allowLoneSeat}
                                        onChange={(e) => setAllowLoneSeat(e.target.value)}
                                    >
                                        <option value="">No rule</option>
                                        <option value="true">Allowed</option>
                                        <option value="false">Not allowed</option>
                                    </select>
                                </label>
                            </section>
                        </div>

                        {(errorMessage || successMessage) && (
                            <div className="create-event-feedback">
                                {errorMessage && <p className="create-event-error">{errorMessage}</p>}
                                {successMessage && <p className="create-event-success">{successMessage}</p>}
                            </div>
                        )}

                        <div className="create-event-actions">
                            <button
                                type="button"
                                className="create-event-button create-event-button--secondary"
                                onClick={onBackToEvent}
                                disabled={isSubmitting}
                            >
                                Cancel
                            </button>

                            <button
                                className="create-event-submit"
                                type="submit"
                                disabled={isSubmitting}
                            >
                                {isSubmitting ? "Saving changes..." : "Save changes"}
                            </button>
                        </div>
                    </form>
                </section>
            )}
        </main>
    );
}