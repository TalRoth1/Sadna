import { useEffect, useState, type FormEvent } from "react";
import { getCurrentUser, type CurrentUser } from "../../services/currentUserService";
import { getMyCompanies, type CompanyMembership } from "../../services/companyService";
import { createEvent, type CreateEventRequest } from "../../services/eventService";
import "./CreateEventPage.css";

type EventPolicy = "REGULAR" | "QUEUE" | "LOTTERY";

type CreateEventPageProps = {
    onCreateCompany: () => void;
    onLogin: () => void;
    onRegister: () => void;
    onEventCreated?: (eventId: string) => void;
};

type FormErrors = {
    companyId?: string;
    name?: string;
    date?: string;
    location?: string;
    type?: string;
    ticketPrice?: string;
    availableTickets?: string;
    policy?: string;
    registrationOpen?: string;
    registrationClose?: string;
    maxTicketsPerRegistration?: string;
    queueStart?: string;
    queueCapacity?: string;
};

const policyOptions: { value: EventPolicy; label: string }[] = [
    { value: "REGULAR", label: "Regular sale" },
    { value: "QUEUE", label: "Queue-based sale" },
    { value: "LOTTERY", label: "Lottery registration" },
];

const statusOptions = [
    { value: "ACTIVE", label: "Active" },
    { value: "SUSPENDED", label: "Suspended" },
    { value: "CLOSED", label: "Closed" },
];

export default function CreateEventPage({
    onCreateCompany,
    onLogin,
    onRegister,
    onEventCreated,
}: CreateEventPageProps) {
    const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
    const [companies, setCompanies] = useState<CompanyMembership[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [companyId, setCompanyId] = useState("");
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [date, setDate] = useState("");
    const [location, setLocation] = useState("");
    const [artist, setArtist] = useState("");
    const [type, setType] = useState("");
    const [status, setStatus] = useState("ACTIVE");
    const [ticketPrice, setTicketPrice] = useState("");
    const [availableTickets, setAvailableTickets] = useState("");
    const [policy, setPolicy] = useState<EventPolicy>("REGULAR");
    const [registrationOpen, setRegistrationOpen] = useState("");
    const [registrationClose, setRegistrationClose] = useState("");
    const [maxTicketsPerRegistration, setMaxTicketsPerRegistration] = useState("");
    const [queueStart, setQueueStart] = useState("");
    const [queueCapacity, setQueueCapacity] = useState("");

    const [errors, setErrors] = useState<FormErrors>({});
    const [successMessage, setSuccessMessage] = useState("");
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        async function loadData() {
            try {
                setIsLoading(true);
                const user = await getCurrentUser();
                setCurrentUser(user);

                if (!user || user.role === "GUEST") {
                    setCompanies([]);
                    return;
                }

                const userCompanies = await getMyCompanies(user.email);
                setCompanies(userCompanies);

                if (userCompanies.length > 0) {
                    setCompanyId(userCompanies[0].companyId);
                }
            } catch {
                setErrorMessage("Failed to load your companies.");
            } finally {
                setIsLoading(false);
            }
        }

        loadData();
    }, []);

    function validateForm() {
        const validation: FormErrors = {};

        if (!companyId) {
            validation.companyId = "Please select a company.";
        }
        if (!name.trim()) {
            validation.name = "Event name is required.";
        }
        if (!date) {
            validation.date = "Event date and time are required.";
        }
        if (!location.trim()) {
            validation.location = "Location is required.";
        }
        if (!type.trim()) {
            validation.type = "Category or event type is required.";
        }
        if (!ticketPrice.trim() || Number.isNaN(Number(ticketPrice)) || Number(ticketPrice) < 0) {
            validation.ticketPrice = "Enter a valid ticket price.";
        }
        if (
            !availableTickets.trim() ||
            Number.isNaN(Number(availableTickets)) ||
            Number(availableTickets) < 0 ||
            !Number.isInteger(Number(availableTickets))
        ) {
            validation.availableTickets = "Enter a valid ticket quantity.";
        }
        if (!policy) {
            validation.policy = "Select a purchase policy.";
        }
        if (policy === "LOTTERY") {
            if (!registrationOpen) {
                validation.registrationOpen = "Registration opening date/time is required.";
            }
            if (!registrationClose) {
                validation.registrationClose = "Registration closing date/time is required.";
            }
            if (
                !maxTicketsPerRegistration.trim() ||
                Number.isNaN(Number(maxTicketsPerRegistration)) ||
                Number(maxTicketsPerRegistration) < 1 ||
                !Number.isInteger(Number(maxTicketsPerRegistration))
            ) {
                validation.maxTicketsPerRegistration = "Enter a valid maximum tickets per registration.";
            }
        }
        if (policy === "QUEUE") {
            if (!queueStart) {
                validation.queueStart = "Queue opening date/time is required.";
            }
            if (
                queueCapacity.trim() &&
                (Number.isNaN(Number(queueCapacity)) || Number(queueCapacity) < 1 || !Number.isInteger(Number(queueCapacity)))
            ) {
                validation.queueCapacity = "Enter a valid queue capacity or leave blank.";
            }
        }

        return validation;
    }

    function resetForm() {
        setName("");
        setDescription("");
        setDate("");
        setLocation("");
        setArtist("");
        setType("");
        setStatus("ACTIVE");
        setTicketPrice("");
        setAvailableTickets("");
        setPolicy("REGULAR");
        setRegistrationOpen("");
        setRegistrationClose("");
        setMaxTicketsPerRegistration("");
        setQueueStart("");
        setQueueCapacity("");
        setErrors({});
    }

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setSuccessMessage("");
        setErrorMessage("");

        const validationErrors = validateForm();
        setErrors(validationErrors);

        if (Object.keys(validationErrors).length > 0) {
            setErrorMessage("Please fix the highlighted fields before submitting.");
            return;
        }

        try {
            setIsSubmitting(true);

            const request: CreateEventRequest = {
                companyId,
                name: name.trim(),
                date,
                location: location.trim(),
                artist: artist.trim(),
                type: type.trim(),
                status,
                description: description.trim() || undefined,
                ticketPrice: ticketPrice.trim() ? Number(ticketPrice) : undefined,
                availableTickets: availableTickets.trim() ? Number(availableTickets) : undefined,
            };

            const createdEvent = await createEvent(request);

            setSuccessMessage("Event created successfully.");
            resetForm();

            const createdEventId =
                createdEvent && typeof createdEvent === "object" && "eventId" in createdEvent
                    ? (createdEvent as { eventId: string }).eventId
                    : null;

            if (createdEventId && onEventCreated) {
                onEventCreated(createdEventId);
            }
        } catch (error) {
            setErrorMessage(
                error instanceof Error
                    ? error.message
                    : "Failed to create event. Please try again.",
            );
        } finally {
            setIsSubmitting(false);
        }
    }

    const isGuest = currentUser === null || currentUser.role === "GUEST";

    return (
        <main className="app-page create-event-page">
            <section className="page-header">
                <h1>Create Event</h1>
                <p>Create a new event under one of the production companies you belong to.</p>
            </section>

            {isLoading && (
                <section className="empty-state">
                    <h2>Loading...</h2>
                    <p>Please wait while we load your account and company membership.</p>
                </section>
            )}

            {!isLoading && isGuest && (
                <section className="empty-state create-event-access-card">
                    <h2>Member access required</h2>
                    <p>This page is available to logged-in members only.</p>
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

            {!isLoading && !isGuest && companies.length === 0 && (
                <section className="empty-state create-event-access-card">
                    <h2>No companies available</h2>
                    <p>
                        You can create an event only under a company you belong to.
                        Please create a company first or ask a company owner to invite you.
                    </p>
                    <button type="button" className="create-event-button" onClick={onCreateCompany}>
                        Create company
                    </button>
                </section>
            )}

            {!isLoading && !isGuest && companies.length > 0 && (
                <section className="create-event-card">
                    <form className="create-event-form" onSubmit={handleSubmit} noValidate>
                        <div className="create-event-grid">
                            <section className="create-event-panel">
                                <h2>Event details</h2>
                                <label className="form-field">
                                    <span>Company</span>
                                    <select value={companyId} onChange={(e) => setCompanyId(e.target.value)}>
                                        {companies.map((company) => (
                                            <option key={company.companyId} value={company.companyId}>
                                                {company.companyName}
                                            </option>
                                        ))}
                                    </select>
                                    {errors.companyId && <small>{errors.companyId}</small>}
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
                                    <span>Event description</span>
                                    <textarea
                                        value={description}
                                        onChange={(e) => setDescription(e.target.value)}
                                        placeholder="Describe the event"
                                        rows={4}
                                    />
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
                                    <span>Artist / performer</span>
                                    <input
                                        type="text"
                                        value={artist}
                                        onChange={(e) => setArtist(e.target.value)}
                                        placeholder="Artist or headline performer"
                                    />
                                </label>

                                <label className="form-field">
                                    <span>Event status</span>
                                    <select value={status} onChange={(e) => setStatus(e.target.value)}>
                                        {statusOptions.map((option) => (
                                            <option key={option.value} value={option.value}>
                                                {option.label}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                            </section>

                            <section className="create-event-panel">
                                <h2>Ticket & filter data</h2>

                                <label className="form-field">
                                    <span>Ticket price</span>
                                    <input
                                        type="number"
                                        min={0}
                                        step="0.01"
                                        value={ticketPrice}
                                        onChange={(e) => setTicketPrice(e.target.value)}
                                        placeholder="0.00"
                                    />
                                    {errors.ticketPrice && <small>{errors.ticketPrice}</small>}
                                </label>

                                <label className="form-field">
                                    <span>Available ticket quantity</span>
                                    <input
                                        type="number"
                                        min={0}
                                        step="1"
                                        value={availableTickets}
                                        onChange={(e) => setAvailableTickets(e.target.value)}
                                        placeholder="Number of tickets"
                                    />
                                    {errors.availableTickets && <small>{errors.availableTickets}</small>}
                                </label>

                                <p className="form-note">
                                    This information helps later filtering by name, date, location, and price range.
                                </p>

                                <h3>Purchase / registration policy</h3>
                                <div className="policy-options">
                                    {policyOptions.map((option) => (
                                        <label key={option.value} className="policy-option">
                                            <input
                                                type="radio"
                                                name="eventPolicy"
                                                value={option.value}
                                                checked={policy === option.value}
                                                onChange={() => setPolicy(option.value)}
                                            />
                                            <span>{option.label}</span>
                                        </label>
                                    ))}
                                    {errors.policy && <small>{errors.policy}</small>}
                                </div>

                                {policy === "QUEUE" && (
                                    <div className="policy-panel">
                                        <label className="form-field">
                                            <span>Queue opening date/time</span>
                                            <input
                                                type="datetime-local"
                                                value={queueStart}
                                                onChange={(e) => setQueueStart(e.target.value)}
                                            />
                                            {errors.queueStart && <small>{errors.queueStart}</small>}
                                        </label>

                                        <label className="form-field">
                                            <span>Queue capacity</span>
                                            <input
                                                type="number"
                                                min={1}
                                                step="1"
                                                value={queueCapacity}
                                                onChange={(e) => setQueueCapacity(e.target.value)}
                                                placeholder="Optional"
                                            />
                                            {errors.queueCapacity && <small>{errors.queueCapacity}</small>}
                                        </label>

                                        <p className="form-help-text">
                                            Add queue settings if your event requires a waiting line before sales open.
                                        </p>
                                    </div>
                                )}

                                {policy === "LOTTERY" && (
                                    <div className="policy-panel">
                                        <label className="form-field">
                                            <span>Registration opening date/time</span>
                                            <input
                                                type="datetime-local"
                                                value={registrationOpen}
                                                onChange={(e) => setRegistrationOpen(e.target.value)}
                                            />
                                            {errors.registrationOpen && <small>{errors.registrationOpen}</small>}
                                        </label>

                                        <label className="form-field">
                                            <span>Registration closing date/time</span>
                                            <input
                                                type="datetime-local"
                                                value={registrationClose}
                                                onChange={(e) => setRegistrationClose(e.target.value)}
                                            />
                                            {errors.registrationClose && <small>{errors.registrationClose}</small>}
                                        </label>

                                        <label className="form-field">
                                            <span>Max tickets per registration</span>
                                            <input
                                                type="number"
                                                min={1}
                                                step="1"
                                                value={maxTicketsPerRegistration}
                                                onChange={(e) => setMaxTicketsPerRegistration(e.target.value)}
                                            />
                                            {errors.maxTicketsPerRegistration && (
                                                <small>{errors.maxTicketsPerRegistration}</small>
                                            )}
                                        </label>

                                        <p className="form-help-text">
                                            The lottery details are stored locally until the backend endpoint is available.
                                        </p>
                                    </div>
                                )}
                            </section>
                        </div>

                        {(errorMessage || successMessage) && (
                            <div className="create-event-feedback">
                                {errorMessage && <p className="create-event-error">{errorMessage}</p>}
                                {successMessage && <p className="create-event-success">{successMessage}</p>}
                            </div>
                        )}

                        <button className="create-event-submit" type="submit" disabled={isSubmitting}>
                            {isSubmitting ? "Creating event..." : "Create event"}
                        </button>
                    </form>
                </section>
            )}
        </main>
    );
}
