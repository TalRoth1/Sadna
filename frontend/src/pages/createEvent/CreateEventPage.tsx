import { useEffect, useState, type FormEvent } from "react";
import { getCurrentUser, type CurrentUser } from "../../services/currentUserService";
import { getMyCompanies, type CompanyMembership, type EventDiscountType } from "../../services/companyService";
import {
    createEvent,
    type CreateEventRequest,
    type EventStatusValue,
} from "../../services/eventService";

import "./CreateEventPage.css";
import { createLotteryEvent } from "../../services/lotteryService";

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

type DiscountType = "OVERT" | "COUPON" | "CONDITIONAL";

type DiscountDraft = {
    id: string;
    discountType: DiscountType;
    discountPercent: string;
    discountFromDate: string;
    discountToDate: string;
    couponCode: string;
    requiredTickets: string;
    appliedTickets: string;
};

type FormErrors = {
    companyId?: string;
    name?: string;
    date?: string;
    location?: string;
    type?: string;
    ticketAreas?: string;
    lottery?: string;
    discount?: string;
};

const statusOptions: Array<{ value: EventStatusValue; label: string }> = [
    { value: "ACTIVE", label: "Active" },
    { value: "CANCELED", label: "Canceled" },
    { value: "ENDED", label: "Ended" },
];

function createLocalId() {
    if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
        return crypto.randomUUID();
    }

    return `local-${Date.now()}-${Math.random().toString(16).slice(2)}`;
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
    const [status, setStatus] = useState<EventStatusValue>("ACTIVE");

    const [isLotteryEvent, setIsLotteryEvent] = useState(false);
    const [lotteryRegistrationOpen, setLotteryRegistrationOpen] = useState("");
    const [lotteryRegistrationClose, setLotteryRegistrationClose] = useState("");

    const [minAge, setMinAge] = useState("");
    const [minTickets, setMinTickets] = useState("");
    const [maxTickets, setMaxTickets] = useState("");
    const [allowLoneSeat, setAllowLoneSeat] = useState("");

    const [discountPolicyType, setDiscountPolicyType] = useState<EventDiscountType>("MAX");
    const [discounts, setDiscounts] = useState<DiscountDraft[]>([]);

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

        const parsedMinAge = minAge.trim() ? Number(minAge) : null;
        const parsedMinTickets = minTickets.trim() ? Number(minTickets) : null;
        const parsedMaxTickets = maxTickets.trim() ? Number(maxTickets) : null;

        if (
            parsedMinAge !== null &&
            (Number.isNaN(parsedMinAge) || parsedMinAge < 0 || !Number.isInteger(parsedMinAge))
        ) {
            validation.ticketAreas = "Minimum age must be a non-negative whole number.";
        }

        if (
            parsedMinTickets !== null &&
            (Number.isNaN(parsedMinTickets) || parsedMinTickets < 1 || !Number.isInteger(parsedMinTickets))
        ) {
            validation.ticketAreas = "Minimum tickets per purchase must be a positive whole number.";
        }

        if (
            parsedMaxTickets !== null &&
            (Number.isNaN(parsedMaxTickets) || parsedMaxTickets < 1 || !Number.isInteger(parsedMaxTickets))
        ) {
            validation.ticketAreas = "Maximum tickets per purchase must be a positive whole number.";
        }

        if (
            parsedMinTickets !== null &&
            parsedMaxTickets !== null &&
            parsedMinTickets > parsedMaxTickets
        ) {
            validation.ticketAreas =
                "Minimum tickets per purchase cannot be greater than maximum tickets per purchase.";
        }

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

        for (const discount of discounts) {
            const percent = Number(discount.discountPercent);

            if (
                !discount.discountPercent.trim() ||
                Number.isNaN(percent) ||
                percent <= 0 ||
                percent > 100
            ) {
                validation.discount = "Each discount percent must be between 1 and 100.";
                break;
            }

            if (!discount.discountToDate) {
                validation.discount = "Each discount must have an end date.";
                break;
            }

            if (discount.discountType === "COUPON" && !discount.couponCode.trim()) {
                validation.discount = "Each coupon discount must have a coupon code.";
                break;
            }

            if (discount.discountType === "CONDITIONAL") {
                const required = Number(discount.requiredTickets);
                const applied = Number(discount.appliedTickets);

                if (
                    !discount.requiredTickets.trim() ||
                    !discount.appliedTickets.trim() ||
                    Number.isNaN(required) ||
                    Number.isNaN(applied) ||
                    required <= 0 ||
                    applied <= 0 ||
                    !Number.isInteger(required) ||
                    !Number.isInteger(applied)
                ) {
                    validation.discount =
                        "Each conditional discount requires positive ticket amounts.";
                    break;
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

        setDiscountPolicyType("MAX");
        setDiscounts([]);
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

    function addDiscount() {
        setDiscounts((current) => [
            ...current,
            {
                id: createLocalId(),
                discountType: "OVERT",
                discountPercent: "",
                discountFromDate: "",
                discountToDate: "",
                couponCode: "",
                requiredTickets: "",
                appliedTickets: "",
            },
        ]);
    }

    function removeDiscount(id: string) {
        setDiscounts((current) =>
            current.filter((discount) => discount.id !== id),
        );
    }

    function updateDiscount(
        id: string,
        field: keyof DiscountDraft,
        value: string,
    ) {
        setDiscounts((current) =>
            current.map((discount) =>
                discount.id === id
                    ? {
                          ...discount,
                          [field]: value,
                      }
                    : discount,
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

    async function addEventDiscounts(
        eventId: string,
        companyId: string,
        username: string,
    ) {
        for (const discount of discounts) {
            const commonBody = {
                username,
                companyId,
                fromDate: discount.discountFromDate || null,
                toDate: discount.discountToDate,
                discountPercent: Number(discount.discountPercent),
            };

            if (discount.discountType === "OVERT") {
                const response = await fetch(`/api/events/${eventId}/discounts/overt`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(commonBody),
                });

                const body = await response.json();

                if (!response.ok || !body.success) {
                    throw new Error(body.message || "Failed to create overt discount.");
                }
            }

            if (discount.discountType === "COUPON") {
                const response = await fetch(`/api/events/${eventId}/discounts/coupon`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        ...commonBody,
                        code: discount.couponCode.trim(),
                    }),
                });

                const body = await response.json();

                if (!response.ok || !body.success) {
                    throw new Error(body.message || "Failed to create coupon discount.");
                }
            }

            if (discount.discountType === "CONDITIONAL") {
                const response = await fetch(`/api/events/${eventId}/discounts/conditional`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        ...commonBody,
                        requiredTickets: Number(discount.requiredTickets),
                        appliedTickets: Number(discount.appliedTickets),
                    }),
                });

                const body = await response.json();

                if (!response.ok || !body.success) {
                    throw new Error(body.message || "Failed to create conditional discount.");
                }
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
                discountType: discountPolicyType,
            };

            const createdEvent = isLotteryEvent
                ? await createLotteryEvent({
                      ...request,
                      type: "Lottery",
                      lottery: {
                          registrationOpen: `${lotteryRegistrationOpen}:00`,
                          registrationClose: `${lotteryRegistrationClose}:00`,
                      },
                  })
                : await createEvent(request);

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

            await addEventPolicyRule(createdEventId, companyId, currentUser.email);
            await addEventDiscounts(createdEventId, companyId, currentUser.email);

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
                                    <span>Discount policy type</span>
                                    <select
                                        value={discountPolicyType}
                                        onChange={(e) =>
                                            setDiscountPolicyType(e.target.value as EventDiscountType)
                                        }
                                    >
                                        <option value="MAX">MAX</option>
                                        <option value="ALL">ALL</option>
                                    </select>
                                    <small>Choose how discount rules combine for this event.</small>
                                </label>

                                <label className="form-field">
                                    <span>Event status</span>
                                    <select
                                        value={status}
                                        onChange={(e) => setStatus(e.target.value as EventStatusValue)}
                                    >
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
                                <h2>Discounts</h2>

                                <p className="form-note">
                                    Optional discount rules for this event. You can add more than one discount.
                                </p>

                                {errors.discount && (
                                    <p className="create-event-error">{errors.discount}</p>
                                )}

                                {discounts.length === 0 && (
                                    <p className="form-note">
                                        No discounts were added.
                                    </p>
                                )}

                                {discounts.map((discount, index) => (
                                    <section key={discount.id} className="ticket-area-card">
                                        <div className="ticket-area-card-header">
                                            <div>
                                                <h3>Discount #{index + 1}</h3>
                                                <p>
                                                    Choose a discount type and fill the relevant fields.
                                                </p>
                                            </div>

                                            <button
                                                type="button"
                                                className="ticket-area-remove"
                                                onClick={() => removeDiscount(discount.id)}
                                            >
                                                Remove
                                            </button>
                                        </div>

                                        <label className="form-field">
                                            <span>Discount type</span>
                                            <select
                                                value={discount.discountType}
                                                onChange={(e) =>
                                                    updateDiscount(
                                                        discount.id,
                                                        "discountType",
                                                        e.target.value as DiscountType,
                                                    )
                                                }
                                            >
                                                <option value="OVERT">Overt discount</option>
                                                <option value="COUPON">Coupon code</option>
                                                <option value="CONDITIONAL">Conditional discount</option>
                                            </select>
                                        </label>

                                        <label className="form-field">
                                            <span>Discount percent</span>
                                            <input
                                                type="number"
                                                min={0}
                                                max={100}
                                                step="1"
                                                value={discount.discountPercent}
                                                onChange={(e) =>
                                                    updateDiscount(
                                                        discount.id,
                                                        "discountPercent",
                                                        e.target.value,
                                                    )
                                                }
                                                placeholder="e.g. 15"
                                            />
                                        </label>

                                        <label className="form-field">
                                            <span>From date</span>
                                            <input
                                                type="date"
                                                value={discount.discountFromDate}
                                                onChange={(e) =>
                                                    updateDiscount(
                                                        discount.id,
                                                        "discountFromDate",
                                                        e.target.value,
                                                    )
                                                }
                                            />
                                        </label>

                                        <label className="form-field">
                                            <span>To date</span>
                                            <input
                                                type="date"
                                                value={discount.discountToDate}
                                                onChange={(e) =>
                                                    updateDiscount(
                                                        discount.id,
                                                        "discountToDate",
                                                        e.target.value,
                                                    )
                                                }
                                            />
                                        </label>

                                        {discount.discountType === "COUPON" && (
                                            <label className="form-field">
                                                <span>Coupon code</span>
                                                <input
                                                    type="text"
                                                    value={discount.couponCode}
                                                    onChange={(e) =>
                                                        updateDiscount(
                                                            discount.id,
                                                            "couponCode",
                                                            e.target.value,
                                                        )
                                                    }
                                                    placeholder="e.g. JAZZ20"
                                                />
                                            </label>
                                        )}

                                        {discount.discountType === "CONDITIONAL" && (
                                            <>
                                                <label className="form-field">
                                                    <span>Required tickets</span>
                                                    <input
                                                        type="number"
                                                        min={1}
                                                        step="1"
                                                        value={discount.requiredTickets}
                                                        onChange={(e) =>
                                                            updateDiscount(
                                                                discount.id,
                                                                "requiredTickets",
                                                                e.target.value,
                                                            )
                                                        }
                                                        placeholder="e.g. 3"
                                                    />
                                                </label>

                                                <label className="form-field">
                                                    <span>Applied free/discounted tickets</span>
                                                    <input
                                                        type="number"
                                                        min={1}
                                                        step="1"
                                                        value={discount.appliedTickets}
                                                        onChange={(e) =>
                                                            updateDiscount(
                                                                discount.id,
                                                                "appliedTickets",
                                                                e.target.value,
                                                            )
                                                        }
                                                        placeholder="e.g. 1"
                                                    />
                                                </label>
                                            </>
                                        )}
                                    </section>
                                ))}

                                <div className="create-event-actions create-event-actions--left">
                                    <button
                                        type="button"
                                        className="create-event-button create-event-button--secondary"
                                        onClick={addDiscount}
                                    >
                                        Add discount
                                    </button>
                                </div>
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