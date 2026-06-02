import { useEffect, useState, type FormEvent } from "react";
import { getCurrentUser, type CurrentUser } from "../../services/currentUserService";
import { getMyCompanies, type CompanyMembership } from "../../services/companyService";
import { createEvent, type CreateEventRequest } from "../../services/eventService";
import "./CreateEventPage.css";
import { createLotteryForEvent } from "../../services/lotteryService";

type CreateEventPageProps = {
    initialCompanyId?: string | null;
    onCreateCompany: () => void;
    onLogin: () => void;
    onRegister: () => void;
    onEventCreated?: (eventId: string) => void;
};

type TicketAreaType = "STANDING" | "SITTING";

type TicketAreaDraft = {
    id: string;
    areaType: TicketAreaType;
    price: string;
    capacity: string;
    rows: string;
    seatsPerRow: string;
};

type FormErrors = {
    companyId?: string;
    name?: string;
    date?: string;
    location?: string;
    type?: string;
    ticketAreas?: string;
    lottery?: string;
};

const statusOptions = [
    { value: "ACTIVE", label: "Active" },
    { value: "SUSPENDED", label: "Suspended" },
    { value: "CLOSED", label: "Closed" },
];

function createLocalId() {
    return crypto.randomUUID();
}

function getCreatedEventId(createdEvent: unknown): string | null {
    if (!createdEvent || typeof createdEvent !== "object") return null;

    const obj = createdEvent as any;

    return (
        obj.eventId ??
        obj.id ??
        obj.data?.eventId ??
        obj.data?.id ??
        null
    );
}

async function addStandingArea(eventId: string, area: TicketAreaDraft) {
    const response = await fetch(`/api/events/${eventId}/areas/standing`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            price: Number(area.price),
            count: Number(area.capacity),
        }),
    });

    const body = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to create standing area.");
    }
}

async function addSittingArea(eventId: string, area: TicketAreaDraft) {
    const response = await fetch(`/api/events/${eventId}/areas/sitting`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            price: Number(area.price),
            rows: Number(area.rows),
            seatsPerRow: Number(area.seatsPerRow),
        }),
    });

    const body = await response.json();

    if (!response.ok || !body.success) {
        throw new Error(body.message || "Failed to create sitting area.");
    }
}

export default function CreateEventPage({
    initialCompanyId,
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
    const [isLotteryEvent, setIsLotteryEvent] = useState(false);
const [lotteryRegistrationOpen, setLotteryRegistrationOpen] = useState("");
const [lotteryRegistrationClose, setLotteryRegistrationClose] = useState("");
    const [minAge, setMinAge] = useState("");
    const [minTickets, setMinTickets] = useState("");
    const [maxTickets, setMaxTickets] = useState("");
    const [allowLoneSeat, setAllowLoneSeat] = useState("");

    const [discountType, setDiscountType] = useState("NONE");
    const [discountPercent, setDiscountPercent] = useState("");
    const [discountFromDate, setDiscountFromDate] = useState("");
    const [discountToDate, setDiscountToDate] = useState("");
    const [couponCode, setCouponCode] = useState("");
    const [requiredTickets, setRequiredTickets] = useState("");
    const [appliedTickets, setAppliedTickets] = useState("");

    const [ticketAreas, setTicketAreas] = useState<TicketAreaDraft[]>([
        {
            id: createLocalId(),
            areaType: "STANDING",
            price: "",
            capacity: "",
            rows: "",
            seatsPerRow: "",
        },
    ]);

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

                const lastCompanyId = localStorage.getItem("lastCreateEventCompanyId");

                if (userCompanies.length > 0) {
                    const selectedCompanyId =
                        initialCompanyId &&
                        userCompanies.some((company) => company.companyId === initialCompanyId)
                            ? initialCompanyId
                            : lastCompanyId &&
                                userCompanies.some((company) => company.companyId === lastCompanyId)
                                    ? lastCompanyId
                                    : userCompanies[0].companyId;

                    setCompanyId(selectedCompanyId);
                }
            } catch {
                setErrorMessage("Failed to load your companies.");
            } finally {
                setIsLoading(false);
            }
        }

        loadData();
    }, [initialCompanyId]);

    function validateForm() {
        const validation: FormErrors = {};

        if (!companyId) validation.companyId = "Please select a company.";
        if (!name.trim()) validation.name = "Event name is required.";
        if (!date) validation.date = "Event date and time are required.";
        if (!location.trim()) validation.location = "Location is required.";
        if (!type.trim()) validation.type = "Category or event type is required.";

        if (ticketAreas.length === 0) {
            validation.ticketAreas = "Add at least one ticket area.";
        } else {
            for (const area of ticketAreas) {
                const price = Number(area.price);

                if (!area.price.trim() || Number.isNaN(price) || price < 0) {
                    validation.ticketAreas = "Each area must have a valid ticket price.";
                    break;
                }

                if (area.areaType === "STANDING") {
                    const capacity = Number(area.capacity);
                    if (
                        !area.capacity.trim() ||
                        Number.isNaN(capacity) ||
                        capacity < 1 ||
                        !Number.isInteger(capacity)
                    ) {
                        validation.ticketAreas = "Each standing area must have a valid capacity.";
                        break;
                    }
                }

                if (area.areaType === "SITTING") {
                    const rows = Number(area.rows);
                    const seatsPerRow = Number(area.seatsPerRow);

                    if (
                        !area.rows.trim() ||
                        !area.seatsPerRow.trim() ||
                        Number.isNaN(rows) ||
                        Number.isNaN(seatsPerRow) ||
                        rows < 1 ||
                        seatsPerRow < 1 ||
                        !Number.isInteger(rows) ||
                        !Number.isInteger(seatsPerRow)
                    ) {
                        validation.ticketAreas = "Each sitting area must have valid rows and seats per row.";
                        break;
                    }
                }
            }
        }
        if (isLotteryEvent) {
            if (!lotteryRegistrationOpen || !lotteryRegistrationClose) {
                validation.lottery = "Lottery registration open and close dates are required.";
            } else {
                const openDate = new Date(lotteryRegistrationOpen);
                const closeDate = new Date(lotteryRegistrationClose);

                if (
                    Number.isNaN(openDate.getTime()) ||
                    Number.isNaN(closeDate.getTime())
                ) {
                    validation.lottery = "Lottery registration dates are invalid.";
                } else if (closeDate <= openDate) {
                    validation.lottery = "Lottery registration close time must be after open time.";
                }
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
        setIsLotteryEvent(false);
        setLotteryRegistrationOpen("");
        setLotteryRegistrationClose("");
        setTicketAreas([
            {
                id: createLocalId(),
                areaType: "STANDING",
                price: "",
                capacity: "",
                rows: "",
                seatsPerRow: "",
            },
        ]);
        setErrors({});
        setMinAge("");
        setMinTickets("");
        setMaxTickets("");
        setAllowLoneSeat("");

        setDiscountType("NONE");
        setDiscountPercent("");
        setDiscountFromDate("");
        setDiscountToDate("");
        setCouponCode("");
        setRequiredTickets("");
        setAppliedTickets("");
    }

    function addTicketArea(areaType: TicketAreaType) {
        setTicketAreas((current) => [
            ...current,
            {
                id: createLocalId(),
                areaType,
                price: "",
                capacity: "",
                rows: "",
                seatsPerRow: "",
            },
        ]);
    }

    function removeTicketArea(id: string) {
        setTicketAreas((current) => current.filter((area) => area.id !== id));
    }

    function updateTicketArea(id: string, field: keyof TicketAreaDraft, value: string) {
        setTicketAreas((current) =>
            current.map((area) =>
                area.id === id ? { ...area, [field]: value } : area,
            ),
        );
    }
    async function addEventPolicyRule(
            eventId: string,
            companyId: string,
            username: string,
        ) {
            const hasPolicy =
                minAge.trim() ||
                minTickets.trim() ||
                maxTickets.trim() ||
                allowLoneSeat !== "";

            if (!hasPolicy) {
                return;
            }

            const response = await fetch(`/api/events/${eventId}/policy`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    username,
                    companyId,
                    age: minAge.trim() ? Number(minAge) : null,
                    minTicket: minTickets.trim() ? Number(minTickets) : null,
                    maxTicket: maxTickets.trim() ? Number(maxTickets) : null,
                    allowLoneSeat: allowLoneSeat === "" ? null : allowLoneSeat === "true",
                }),
            });

            const body = await response.json();

            if (!response.ok || !body.success) {
                throw new Error(body.message || "Failed to create event policy.");
            }
        }

        async function addEventDiscount(
            eventId: string,
            companyId: string,
            username: string,
        ) {
            if (discountType === "NONE") {
                return;
            }

            if (!discountPercent.trim() || !discountToDate) {
                throw new Error("Discount percent and end date are required.");
            }

            if (discountType === "OVERT") {
                const response = await fetch(`/api/events/${eventId}/discounts/overt`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        username,
                        companyId,
                        fromDate: discountFromDate || null,
                        toDate: discountToDate,
                        discountPercent: Number(discountPercent),
                    }),
                });

                const body = await response.json();

                if (!response.ok || !body.success) {
                    throw new Error(body.message || "Failed to create overt discount.");
                }

                return;
            }

            if (discountType === "COUPON") {
                if (!couponCode.trim()) {
                    throw new Error("Coupon code is required.");
                }

                const response = await fetch(`/api/events/${eventId}/discounts/coupon`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        username,
                        companyId,
                        fromDate: discountFromDate || null,
                        toDate: discountToDate,
                        discountPercent: Number(discountPercent),
                        code: couponCode.trim(),
                    }),
                });

                const body = await response.json();

                if (!response.ok || !body.success) {
                    throw new Error(body.message || "Failed to create coupon discount.");
                }

                return;
            }

            if (discountType === "CONDITIONAL") {
                if (!requiredTickets.trim() || !appliedTickets.trim()) {
                    throw new Error("Required tickets and applied tickets are required.");
                }

                const response = await fetch(`/api/events/${eventId}/discounts/conditional`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        username,
                        companyId,
                        fromDate: discountFromDate || null,
                        toDate: discountToDate,
                        discountPercent: Number(discountPercent),
                        requiredTickets: Number(requiredTickets),
                        appliedTickets: Number(appliedTickets),
                    }),
                });

                const body = await response.json();

                if (!response.ok || !body.success) {
                    throw new Error(body.message || "Failed to create conditional discount.");
                }
            }
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

            if (!currentUser?.email) {
                setErrorMessage("Could not identify the current user. Please log in again.");
                return;
            }

            const request: CreateEventRequest = {
                companyId,
                eventManagerEmail: currentUser.email,
                name: name.trim(),
                date: `${date}:00`,
                location: location.trim(),
                artist: artist.trim(),
                type: isLotteryEvent ? "Lottery" : type.trim(),
                status,
                description: description.trim() || undefined,
            };

            const createdEvent = await createEvent(request);
            const createdEventId = getCreatedEventId(createdEvent);

            if (!createdEventId) {
                throw new Error("Event was created, but the server did not return an event id.");
            }

            for (const area of ticketAreas) {
                if (area.areaType === "STANDING") {
                    await addStandingArea(createdEventId, area);
                } else {
                    await addSittingArea(createdEventId, area);
                }
            }

            if (isLotteryEvent) {
                await createLotteryForEvent(createdEventId, {
                    registrationOpen: `${lotteryRegistrationOpen}:00`,
                    registrationClose: `${lotteryRegistrationClose}:00`,
                });
            }

            await addEventPolicyRule(createdEventId, companyId, currentUser.email);
            await addEventDiscount(createdEventId, companyId, currentUser.email);   

            setSuccessMessage(
                isLotteryEvent
                    ? "Lottery event, ticket areas, policies and discounts created successfully."
                    : "Event, ticket areas, policies and discounts created successfully.",
            );
            resetForm();

            if (onEventCreated) {
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
                <p>Create a base event first, then configure one or more ticket areas.</p>
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
                                    <select
                                        value={companyId}
                                        onChange={(e) => {
                                            setCompanyId(e.target.value);
                                            localStorage.setItem("lastCreateEventCompanyId", e.target.value);
                                        }}
                                    >
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
                                    <select value={status} onChange={(e) => setStatus(e.target.value)}>
                                        {statusOptions.map((option) => (
                                            <option key={option.value} value={option.value}>
                                                {option.label}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                <div className="form-field">
                                    <label className="create-event-checkbox-row">
                                        <input
                                            type="checkbox"
                                            checked={isLotteryEvent}
                                            onChange={(e) => {
                                                const checked = e.target.checked;
                                                setIsLotteryEvent(checked);

                                                if (checked && !type.trim()) {
                                                    setType("Lottery");
                                                }
                                            }}
                                        />
                                        <span>Make this event a lottery event</span>
                                    </label>

                                    <small>
                                        Users will register first. Later, event managers can draw winners and winners receive access codes.
                                    </small>
                                </div>

                                {isLotteryEvent && (
                                    <>
                                        <label className="form-field">
                                            <span>Lottery registration opens</span>
                                            <input
                                                type="datetime-local"
                                                value={lotteryRegistrationOpen}
                                                onChange={(e) => setLotteryRegistrationOpen(e.target.value)}
                                            />
                                        </label>

                                        <label className="form-field">
                                            <span>Lottery registration closes</span>
                                            <input
                                                type="datetime-local"
                                                value={lotteryRegistrationClose}
                                                onChange={(e) => setLotteryRegistrationClose(e.target.value)}
                                            />
                                        </label>

                                        {errors.lottery && (
                                            <p className="create-event-error">{errors.lottery}</p>
                                        )}
                                    </>
                                )}

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
                                <div className="ticket-area-header">
                                    <div>
                                        <h2>Ticket areas</h2>
                                        <p className="form-note">
                                            Pricing and availability are calculated from the areas you create.
                                        </p>
                                    </div>
                                </div>

                                <div className="create-event-actions create-event-actions--left">
                                    <button
                                        type="button"
                                        className="create-event-button"
                                        onClick={() => addTicketArea("STANDING")}
                                    >
                                        Add standing area
                                    </button>

                                    <button
                                        type="button"
                                        className="create-event-button create-event-button--secondary"
                                        onClick={() => addTicketArea("SITTING")}
                                    >
                                        Add sitting area
                                    </button>
                                </div>

                                {errors.ticketAreas && <p className="create-event-error">{errors.ticketAreas}</p>}

                                <div className="ticket-area-list">
                                    {ticketAreas.map((area, index) => (
                                        <section key={area.id} className="ticket-area-card">
                                            <div className="ticket-area-card-header">
                                                <div>
                                                    <h3>
                                                        {area.areaType === "STANDING"
                                                            ? "Standing Area"
                                                            : "Sitting Area"}{" "}
                                                        #{index + 1}
                                                    </h3>
                                                    <p>
                                                        {area.areaType === "STANDING"
                                                            ? "General admission tickets."
                                                            : "Seats are generated by rows and seats per row."}
                                                    </p>
                                                </div>

                                                {ticketAreas.length > 1 && (
                                                    <button
                                                        type="button"
                                                        className="ticket-area-remove"
                                                        onClick={() => removeTicketArea(area.id)}
                                                    >
                                                        Remove
                                                    </button>
                                                )}
                                            </div>

                                            <label className="form-field">
                                                <span>Ticket price</span>
                                                <input
                                                    type="number"
                                                    min={0}
                                                    step="0.01"
                                                    value={area.price}
                                                    onChange={(e) =>
                                                        updateTicketArea(area.id, "price", e.target.value)
                                                    }
                                                    placeholder="0.00"
                                                />
                                            </label>

                                            {area.areaType === "STANDING" && (
                                                <label className="form-field">
                                                    <span>Ticket quantity / capacity</span>
                                                    <input
                                                        type="number"
                                                        min={1}
                                                        step="1"
                                                        value={area.capacity}
                                                        onChange={(e) =>
                                                            updateTicketArea(area.id, "capacity", e.target.value)
                                                        }
                                                        placeholder="Number of standing tickets"
                                                    />
                                                </label>
                                            )}

                                            {area.areaType === "SITTING" && (
                                                <div className="ticket-area-mini-grid">
                                                    <label className="form-field">
                                                        <span>Number of rows</span>
                                                        <input
                                                            type="number"
                                                            min={1}
                                                            step="1"
                                                            value={area.rows}
                                                            onChange={(e) =>
                                                                updateTicketArea(area.id, "rows", e.target.value)
                                                            }
                                                            placeholder="Rows"
                                                        />
                                                    </label>

                                                    <label className="form-field">
                                                        <span>Seats per row</span>
                                                        <input
                                                            type="number"
                                                            min={1}
                                                            step="1"
                                                            value={area.seatsPerRow}
                                                            onChange={(e) =>
                                                                updateTicketArea(area.id, "seatsPerRow", e.target.value)
                                                            }
                                                            placeholder="Seats"
                                                        />
                                                    </label>
                                                </div>
                                            )}
                                        </section>
                                    ))}
                                </div>
                            </section>
                            <section className="policy-panel">
                                <h2>Event policy</h2>

                                <p className="form-note">
                                    Optional purchase restrictions for this event.
                                </p>

                                <label className="form-field">
                                    <span>Minimum age</span>
                                    <input
                                        type="number"
                                        min={0}
                                        step="1"
                                        value={minAge}
                                        onChange={(e) => setMinAge(e.target.value)}
                                        placeholder="Optional, e.g. 18"
                                    />
                                </label>

                                <label className="form-field">
                                    <span>Minimum tickets per purchase</span>
                                    <input
                                        type="number"
                                        min={1}
                                        step="1"
                                        value={minTickets}
                                        onChange={(e) => setMinTickets(e.target.value)}
                                        placeholder="Optional"
                                    />
                                </label>

                                <label className="form-field">
                                    <span>Maximum tickets per purchase</span>
                                    <input
                                        type="number"
                                        min={1}
                                        step="1"
                                        value={maxTickets}
                                        onChange={(e) => setMaxTickets(e.target.value)}
                                        placeholder="Optional, e.g. 4"
                                    />
                                </label>

                                <label className="form-field">
                                    <span>Allow lone seat</span>
                                    <select
                                        value={allowLoneSeat}
                                        onChange={(e) => setAllowLoneSeat(e.target.value)}
                                    >
                                        <option value="">No restriction</option>
                                        <option value="true">Allow lone seats</option>
                                        <option value="false">Block lone seats</option>
                                    </select>
                                </label>
                            </section>
                            <section className="policy-panel">
                                <h2>Discount</h2>

                                <p className="form-note">
                                    Optional discount rule for this event.
                                </p>

                                <label className="form-field">
                                    <span>Discount type</span>
                                    <select
                                        value={discountType}
                                        onChange={(e) => setDiscountType(e.target.value)}
                                    >
                                        <option value="NONE">No discount</option>
                                        <option value="OVERT">Overt discount</option>
                                        <option value="COUPON">Coupon code</option>
                                        <option value="CONDITIONAL">Conditional discount</option>
                                    </select>
                                </label>

                                {discountType !== "NONE" && (
                                    <>
                                        <label className="form-field">
                                            <span>Discount percent</span>
                                            <input
                                                type="number"
                                                min={0}
                                                max={100}
                                                step="1"
                                                value={discountPercent}
                                                onChange={(e) => setDiscountPercent(e.target.value)}
                                                placeholder="e.g. 15"
                                            />
                                        </label>

                                        <label className="form-field">
                                            <span>From date</span>
                                            <input
                                                type="date"
                                                value={discountFromDate}
                                                onChange={(e) => setDiscountFromDate(e.target.value)}
                                            />
                                        </label>

                                        <label className="form-field">
                                            <span>To date</span>
                                            <input
                                                type="date"
                                                value={discountToDate}
                                                onChange={(e) => setDiscountToDate(e.target.value)}
                                            />
                                        </label>
                                    </>
                                )}

                                {discountType === "COUPON" && (
                                    <label className="form-field">
                                        <span>Coupon code</span>
                                        <input
                                            type="text"
                                            value={couponCode}
                                            onChange={(e) => setCouponCode(e.target.value)}
                                            placeholder="e.g. JAZZ20"
                                        />
                                    </label>
                                )}

                                {discountType === "CONDITIONAL" && (
                                    <>
                                        <label className="form-field">
                                            <span>Required tickets</span>
                                            <input
                                                type="number"
                                                min={1}
                                                step="1"
                                                value={requiredTickets}
                                                onChange={(e) => setRequiredTickets(e.target.value)}
                                                placeholder="e.g. 3"
                                            />
                                        </label>

                                        <label className="form-field">
                                            <span>Applied free/discounted tickets</span>
                                            <input
                                                type="number"
                                                min={1}
                                                step="1"
                                                value={appliedTickets}
                                                onChange={(e) => setAppliedTickets(e.target.value)}
                                                placeholder="e.g. 1"
                                            />
                                        </label>
                                    </>
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