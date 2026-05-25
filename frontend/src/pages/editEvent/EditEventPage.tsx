import { useEffect, useMemo, useState, type FormEvent } from "react";
import { getEventById } from "../../services/eventSearchService";
import { getCurrentUser, type CurrentUser } from "../../services/currentUserService";
import { getMyCompanies, type CompanyMembership } from "../../services/companyService";
import type { Event, EventStatus } from "../../types/event";
import "../createEvent/CreateEventPage.css";
import {
    addConditionalDiscount,
    addCouponDiscount,
    addOvertDiscount,
    addSittingArea,
    addStandingArea,
    deleteEventArea,
    editEvent,
    editEventPolicy,
    removeEventDiscount,
    updateSittingArea,
    updateStandingArea,
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
    ticketAreas?: string;
    discount?: string;
};

type TicketAreaType = "STANDING" | "SITTING";

type TicketAreaDraft = {
    id: string;
    areaId?: string;
    areaType: TicketAreaType;
    price: string;
    standingCount: string;
    rows: string;
    seatsPerRow: string;
    currentTicketCount: number;
    isNew: boolean;
    isDeleted: boolean;
};

type DiscountType = "NONE" | "OVERT" | "COUPON" | "CONDITIONAL";

const statusOptions: { value: EventStatus; label: string }[] = [
    { value: "ACTIVE", label: "Active" },
    { value: "ENDED", label: "Ended" },
    { value: "CANCELED", label: "Canceled" },
];

function createLocalId() {
    if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
        return crypto.randomUUID();
    }

    return `local-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function toDateTimeLocalValue(date: string) {
    const parsed = new Date(date);

    if (Number.isNaN(parsed.getTime())) {
        return "";
    }

    const offsetMs = parsed.getTimezoneOffset() * 60 * 1000;
    return new Date(parsed.getTime() - offsetMs).toISOString().slice(0, 16);
}

function normalizePermission(value: unknown): string {
    return String(value ?? "")
        .trim()
        .toLowerCase()
        .replace(/[\s_-]/g, "");
}

function canUserEditEvent(
    event: Event | null,
    currentUser: CurrentUser | null,
    companies: CompanyMembership[],
) {
    if (!event || !currentUser) {
        return false;
    }

    const userRole = normalizePermission(currentUser.role);

    if (userRole === "admin" || userRole === "platformadmin") {
        return true;
    }

    const companyMembership = companies.find(
        (company) => company.companyId === event.companyId,
    );

    if (!companyMembership) {
        return false;
    }

    const membership = companyMembership as any;

    const role = normalizePermission(
        membership.role ??
            membership.companyRole ??
            membership.memberRole ??
            membership.roleName,
    );

    const permissions = (
        membership.permissions ??
        membership.permissionNames ??
        membership.companyPermissions ??
        []
    ).map(normalizePermission);

    const managedEventIds = (
        membership.eventIds ??
        membership.eventsIds ??
        membership.managedEventIds ??
        membership.eventIdsManaged ??
        []
    ).map(String);

    return (
        role.includes("founder") ||
        role.includes("owner") ||
        role.includes("manager") ||
        permissions.includes("manageinventory") ||
        permissions.includes("managepolicies") ||
        permissions.includes("configurelayout") ||
        managedEventIds.includes(event.id)
    );
}

function getTicketRow(ticket: any): number | null {
    const row = ticket.row ?? ticket.seatRow ?? ticket.seat_row;
    const parsed = Number(row);

    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function getTicketSeat(ticket: any): number | null {
    const seat = ticket.seat ?? ticket.seatNumber ?? ticket.seat_number;
    const parsed = Number(seat);

    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function computeSittingLayout(event: Event, areaId: string) {
    const tickets = ((event as any).tickets ?? []).filter((ticket: any) => {
        return String(ticket.areaId) === String(areaId);
    });

    let rows = 0;
    let seatsPerRow = 0;

    for (const ticket of tickets) {
        const row = getTicketRow(ticket);
        const seat = getTicketSeat(ticket);

        if (row !== null) {
            rows = Math.max(rows, row);
        }

        if (seat !== null) {
            seatsPerRow = Math.max(seatsPerRow, seat);
        }
    }

    return { rows, seatsPerRow };
}

function buildAreaDrafts(event: Event): TicketAreaDraft[] {
    const rawEvent = event as any;

    const areas =
        rawEvent.areas ??
        rawEvent.layout?.areas ??
        rawEvent.ticketAreas ??
        [];

    const tickets =
        rawEvent.tickets ??
        rawEvent.ticketDetails ??
        rawEvent.inventory?.tickets ??
        [];

    console.log("EDIT EVENT loaded event:", rawEvent);
    console.log("EDIT EVENT areas:", areas);
    console.log("EDIT EVENT tickets:", tickets);

    return areas.map((area: any) => {
        const areaId = String(
            area.areaId ??
                area.id ??
                area.uuid ??
                "",
        );

        const areaType = String(
            area.kind ??
                area.areaType ??
                area.type ??
                "",
        ).toUpperCase() as TicketAreaType;

        const ticketIds =
            area.ticketIds ??
            area.ticketsIds ??
            area.ticketIDs ??
            area.ticketIdsView ??
            [];

        const areaTickets = tickets.filter((ticket: any) => {
            return String(ticket.areaId ?? ticket.areaID) === areaId;
        });

        const currentTicketCount =
            Array.isArray(ticketIds) && ticketIds.length > 0
                ? ticketIds.length
                : areaTickets.length;

        if (areaType === "SITTING") {
            let rows = 0;
            let seatsPerRow = 0;

            for (const ticket of areaTickets) {
                const row = getTicketRow(ticket);
                const seat = getTicketSeat(ticket);

                if (row !== null) {
                    rows = Math.max(rows, row);
                }

                if (seat !== null) {
                    seatsPerRow = Math.max(seatsPerRow, seat);
                }
            }

            return {
                id: areaId || createLocalId(),
                areaId,
                areaType: "SITTING",
                price: String(area.price ?? ""),
                standingCount: "",
                rows: rows > 0 ? String(rows) : "",
                seatsPerRow: seatsPerRow > 0 ? String(seatsPerRow) : "",
                currentTicketCount,
                isNew: false,
                isDeleted: false,
            };
        }

        return {
            id: areaId || createLocalId(),
            areaId,
            areaType: "STANDING",
            price: String(area.price ?? ""),
            standingCount: String(currentTicketCount),
            rows: "",
            seatsPerRow: "",
            currentTicketCount,
            isNew: false,
            isDeleted: false,
        };
    });
}

function hasPolicyFields(
    minAge: string,
    minTickets: string,
    maxTickets: string,
    allowLoneSeat: string,
) {
    return (
        minAge.trim() !== "" ||
        minTickets.trim() !== "" ||
        maxTickets.trim() !== "" ||
        allowLoneSeat !== ""
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

    const [discountType, setDiscountType] = useState<DiscountType>("NONE");
    const [discountPercent, setDiscountPercent] = useState("");
    const [discountFromDate, setDiscountFromDate] = useState("");
    const [discountToDate, setDiscountToDate] = useState("");
    const [couponCode, setCouponCode] = useState("");
    const [requiredTickets, setRequiredTickets] = useState("");
    const [appliedTickets, setAppliedTickets] = useState("");

    const [ticketAreas, setTicketAreas] = useState<TicketAreaDraft[]>([]);

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

                setTicketAreas(buildAreaDrafts(loadedEvent));

                const firstDiscount = loadedEvent.discountPolicy.rules[0];

                if (!firstDiscount) {
                    setDiscountType("NONE");
                    setDiscountPercent("");
                    setDiscountFromDate("");
                    setDiscountToDate("");
                    setCouponCode("");
                    setRequiredTickets("");
                    setAppliedTickets("");
                } else {
                    setDiscountType(firstDiscount.kind as DiscountType);
                    setDiscountPercent(String(firstDiscount.percent ?? ""));
                    setDiscountFromDate(firstDiscount.fromDate ?? "");
                    setDiscountToDate(firstDiscount.toDate ?? "");
                    setCouponCode(firstDiscount.code ?? "");
                    setRequiredTickets(
                        firstDiscount.requiredTickets !== undefined
                            ? String(firstDiscount.requiredTickets)
                            : "",
                    );
                    setAppliedTickets(
                        firstDiscount.appliedTickets !== undefined
                            ? String(firstDiscount.appliedTickets)
                            : "",
                    );
                }

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

    const visibleExistingAreas = ticketAreas.filter(
        (area) => !area.isNew && !area.isDeleted,
    );

    const deletedExistingAreas = ticketAreas.filter(
        (area) => !area.isNew && area.isDeleted,
    );

    const newStandingAreas = ticketAreas.filter(
        (area) => area.isNew && !area.isDeleted && area.areaType === "STANDING",
    );

    const newSittingAreas = ticketAreas.filter(
        (area) => area.isNew && !area.isDeleted && area.areaType === "SITTING",
    );

    function validateForm() {
        const validation: FormErrors = {};

        if (!name.trim()) validation.name = "Event name is required.";
        if (!date) validation.date = "Event date and time are required.";
        if (!location.trim()) validation.location = "Location is required.";
        if (!type.trim()) validation.type = "Category or event type is required.";

        const activeAreas = ticketAreas.filter((area) => !area.isDeleted);

        if (activeAreas.length === 0) {
            validation.ticketAreas = "The event must have at least one ticket area.";
            return validation;
        }

        for (const area of activeAreas) {
            const price = Number(area.price);

            if (!area.price.trim() || Number.isNaN(price) || price < 0) {
                validation.ticketAreas = "Each area must have a valid non-negative price.";
                break;
            }

            if (area.areaType === "STANDING") {
                const count = Number(area.standingCount);

                if (
                    !area.standingCount.trim() ||
                    Number.isNaN(count) ||
                    count <= 0 ||
                    !Number.isInteger(count)
                ) {
                    validation.ticketAreas = "Each standing area must have a positive ticket quantity.";
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
                    rows <= 0 ||
                    seatsPerRow <= 0 ||
                    !Number.isInteger(rows) ||
                    !Number.isInteger(seatsPerRow)
                ) {
                    validation.ticketAreas = "Each sitting area must have valid rows and seats per row.";
                    break;
                }
            }
        }

        if (discountType !== "NONE") {
            const percent = Number(discountPercent);

            if (
                !discountPercent.trim() ||
                Number.isNaN(percent) ||
                percent < 0 ||
                percent > 100
            ) {
                validation.discount = "Discount percent must be between 0 and 100.";
            }

            if (!discountToDate) {
                validation.discount = "Discount end date is required.";
            }

            if (discountType === "COUPON" && !couponCode.trim()) {
                validation.discount = "Coupon code is required.";
            }

            if (discountType === "CONDITIONAL") {
                const required = Number(requiredTickets);
                const applied = Number(appliedTickets);

                if (
                    !requiredTickets.trim() ||
                    !appliedTickets.trim() ||
                    Number.isNaN(required) ||
                    Number.isNaN(applied) ||
                    required <= 0 ||
                    applied <= 0 ||
                    !Number.isInteger(required) ||
                    !Number.isInteger(applied)
                ) {
                    validation.discount = "Conditional discount requires positive ticket amounts.";
                }
            }
        }

        return validation;
    }

    function updateTicketArea(
        id: string,
        field: keyof TicketAreaDraft,
        value: string | boolean | number,
    ) {
        setTicketAreas((current) =>
            current.map((area) =>
                area.id === id ? { ...area, [field]: value } : area,
            ),
        );
    }

    function addNewStandingArea() {
        setTicketAreas((current) => [
            ...current,
            {
                id: createLocalId(),
                areaType: "STANDING",
                price: "",
                standingCount: "",
                rows: "",
                seatsPerRow: "",
                currentTicketCount: 0,
                isNew: true,
                isDeleted: false,
            },
        ]);
    }

    function addNewSittingArea() {
        setTicketAreas((current) => [
            ...current,
            {
                id: createLocalId(),
                areaType: "SITTING",
                price: "",
                standingCount: "",
                rows: "",
                seatsPerRow: "",
                currentTicketCount: 0,
                isNew: true,
                isDeleted: false,
            },
        ]);
    }

    function removeNewArea(id: string) {
        setTicketAreas((current) => current.filter((area) => area.id !== id));
    }

    function markExistingAreaForDeletion(id: string) {
        setTicketAreas((current) =>
            current.map((area) =>
                area.id === id ? { ...area, isDeleted: true } : area,
            ),
        );
    }

    function undoDeleteArea(id: string) {
        setTicketAreas((current) =>
            current.map((area) =>
                area.id === id ? { ...area, isDeleted: false } : area,
            ),
        );
    }

    async function saveDiscountChanges(eventToEdit: Event, username: string) {
        const existingDiscounts = eventToEdit.discountPolicy.rules ?? [];

        for (const discount of existingDiscounts) {
            if (discount.id) {
                await removeEventDiscount(eventToEdit.id, discount.id, {
                    username,
                    companyId: eventToEdit.companyId,
                });
            }
        }

        if (discountType === "NONE") {
            return;
        }

        const commonRequest = {
            username,
            companyId: eventToEdit.companyId,
            fromDate: discountFromDate || null,
            toDate: discountToDate,
            discountPercent: Number(discountPercent),
        };

        if (discountType === "OVERT") {
            await addOvertDiscount(eventToEdit.id, commonRequest);
            return;
        }

        if (discountType === "COUPON") {
            await addCouponDiscount(eventToEdit.id, {
                ...commonRequest,
                code: couponCode.trim(),
            });
            return;
        }

        if (discountType === "CONDITIONAL") {
            await addConditionalDiscount(eventToEdit.id, {
                ...commonRequest,
                requiredTickets: Number(requiredTickets),
                appliedTickets: Number(appliedTickets),
            });
        }
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

            for (const area of deletedExistingAreas) {
                if (!area.areaId) {
                    continue;
                }

                await deleteEventArea(event.id, area.areaId, {
                    username: currentUser.email,
                    companyId: event.companyId,
                });
            }

            for (const area of visibleExistingAreas) {
                if (!area.areaId) {
                    continue;
                }

                const price = Number(area.price);

                if (area.areaType === "STANDING") {
                    await updateStandingArea(event.id, area.areaId, {
                        username: currentUser.email,
                        companyId: event.companyId,
                        price,
                        count: Number(area.standingCount),
                    });
                }

                if (area.areaType === "SITTING") {
                    await updateSittingArea(event.id, area.areaId, {
                        username: currentUser.email,
                        companyId: event.companyId,
                        price,
                        rows: Number(area.rows),
                        seatsPerRow: Number(area.seatsPerRow),
                    });
                }
            }

            for (const area of newStandingAreas) {
                await addStandingArea(event.id, {
                    price: Number(area.price),
                    count: Number(area.standingCount),
                });
            }

            for (const area of newSittingAreas) {
                await addSittingArea(event.id, {
                    price: Number(area.price),
                    rows: Number(area.rows),
                    seatsPerRow: Number(area.seatsPerRow),
                });
            }

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

            await saveDiscountChanges(event, currentUser.email);

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

    function renderStandingAreaFields(area: TicketAreaDraft) {
        return (
            <>
                <label className="form-field">
                    <span>Ticket price</span>
                    <input
                        type="number"
                        min={0}
                        value={area.price}
                        disabled={area.isDeleted}
                        onChange={(e) =>
                            updateTicketArea(area.id, "price", e.target.value)
                        }
                        placeholder="e.g. 120"
                    />
                </label>

                <label className="form-field">
                    <span>Ticket quantity</span>
                    <input
                        type="number"
                        min={1}
                        step={1}
                        value={area.standingCount}
                        disabled={area.isDeleted}
                        onChange={(e) =>
                            updateTicketArea(
                                area.id,
                                "standingCount",
                                e.target.value,
                            )
                        }
                        placeholder="e.g. 100"
                    />
                </label>
            </>
        );
    }

    function renderSittingAreaFields(area: TicketAreaDraft) {
        return (
            <>
                <label className="form-field">
                    <span>Ticket price</span>
                    <input
                        type="number"
                        min={0}
                        value={area.price}
                        disabled={area.isDeleted}
                        onChange={(e) =>
                            updateTicketArea(area.id, "price", e.target.value)
                        }
                        placeholder="e.g. 180"
                    />
                </label>

                <label className="form-field">
                    <span>Rows</span>
                    <input
                        type="number"
                        min={1}
                        step={1}
                        value={area.rows}
                        disabled={area.isDeleted}
                        onChange={(e) =>
                            updateTicketArea(area.id, "rows", e.target.value)
                        }
                        placeholder="e.g. 20"
                    />
                </label>

                <label className="form-field">
                    <span>Seats per row</span>
                    <input
                        type="number"
                        min={1}
                        step={1}
                        value={area.seatsPerRow}
                        disabled={area.isDeleted}
                        onChange={(e) =>
                            updateTicketArea(
                                area.id,
                                "seatsPerRow",
                                e.target.value,
                            )
                        }
                        placeholder="e.g. 10"
                    />
                </label>
            </>
        );
    }

    function renderAreaCard(area: TicketAreaDraft) {
        const title =
            area.areaType === "STANDING" ? "Standing area" : "Sitting area";

        return (
            <article
                key={area.id}
                className="ticket-area-card"
                style={{
                    opacity: area.isDeleted ? 0.55 : 1,
                    border: area.isDeleted ? "1px solid #ef4444" : undefined,
                }}
            >
                <div className="create-event-panel-header">
                    <div>
                        <h3>{title}</h3>
                        {!area.isNew && (
                            <p className="form-note">
                                Current tickets: {area.currentTicketCount}
                            </p>
                        )}
                        {area.isDeleted && (
                            <p className="create-event-error">
                                This area will be deleted after saving.
                            </p>
                        )}
                    </div>

                    {area.isNew ? (
                        <button
                            type="button"
                            className="create-event-remove-area"
                            onClick={() => removeNewArea(area.id)}
                        >
                            Remove
                        </button>
                    ) : area.isDeleted ? (
                        <button
                            type="button"
                            className="create-event-button create-event-button--secondary"
                            onClick={() => undoDeleteArea(area.id)}
                        >
                            Undo delete
                        </button>
                    ) : (
                        <button
                            type="button"
                            className="create-event-remove-area"
                            onClick={() => markExistingAreaForDeletion(area.id)}
                        >
                            Delete area
                        </button>
                    )}
                </div>

                <div className="create-event-grid">
                    {area.areaType === "STANDING"
                        ? renderStandingAreaFields(area)
                        : renderSittingAreaFields(area)}
                </div>
            </article>
        );
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
                        <button
                            type="button"
                            className="create-event-button"
                            onClick={onLogin}
                        >
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
                                        onChange={(e) =>
                                            setStatus(e.target.value as EventStatus)
                                        }
                                    >
                                        {statusOptions.map((option) => (
                                            <option
                                                key={option.value}
                                                value={option.value}
                                            >
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
                                    Edit existing standing and sitting areas, delete areas,
                                    or add new ones.
                                </p>

                                {errors.ticketAreas && (
                                    <p className="create-event-error">
                                        {errors.ticketAreas}
                                    </p>
                                )}

                                {visibleExistingAreas.length === 0 &&
                                    deletedExistingAreas.length === 0 && (
                                        <p className="form-note">
                                            No existing ticket areas.
                                        </p>
                                    )}

                                {visibleExistingAreas.map(renderAreaCard)}
                                {deletedExistingAreas.map(renderAreaCard)}

                                <div className="create-event-actions">
                                    <button
                                        type="button"
                                        className="create-event-button create-event-button--secondary"
                                        onClick={addNewStandingArea}
                                    >
                                        Add standing area
                                    </button>
                                </div>

                                {newStandingAreas.map(renderAreaCard)}

                                <div className="create-event-actions">
                                    <button
                                        type="button"
                                        className="create-event-button create-event-button--secondary"
                                        onClick={addNewSittingArea}
                                    >
                                        Add sitting area
                                    </button>
                                </div>

                                {newSittingAreas.map(renderAreaCard)}
                            </section>

                            <section className="create-event-panel">
                                <h2>Purchase policy</h2>
                                <p className="form-note">
                                    Update purchase restrictions for this event.
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

                                {!hasPolicyFields(
                                    minAge,
                                    minTickets,
                                    maxTickets,
                                    allowLoneSeat,
                                ) && (
                                    <p className="form-note">
                                        Saving with all fields empty will remove the
                                        current purchase policy rules.
                                    </p>
                                )}
                            </section>

                            <section className="create-event-panel">
                                <h2>Discount</h2>
                                <p className="form-note">
                                    Saving this section replaces the existing discount rules.
                                </p>

                                {errors.discount && (
                                    <p className="create-event-error">
                                        {errors.discount}
                                    </p>
                                )}

                                <label className="form-field">
                                    <span>Discount type</span>
                                    <select
                                        value={discountType}
                                        onChange={(e) =>
                                            setDiscountType(e.target.value as DiscountType)
                                        }
                                    >
                                        <option value="NONE">No discount</option>
                                        <option value="OVERT">Overt discount</option>
                                        <option value="COUPON">Coupon code</option>
                                        <option value="CONDITIONAL">
                                            Conditional discount
                                        </option>
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
                                                value={discountPercent}
                                                onChange={(e) =>
                                                    setDiscountPercent(e.target.value)
                                                }
                                                placeholder="e.g. 20"
                                            />
                                        </label>

                                        <label className="form-field">
                                            <span>From date</span>
                                            <input
                                                type="date"
                                                value={discountFromDate}
                                                onChange={(e) =>
                                                    setDiscountFromDate(e.target.value)
                                                }
                                            />
                                        </label>

                                        <label className="form-field">
                                            <span>To date</span>
                                            <input
                                                type="date"
                                                value={discountToDate}
                                                onChange={(e) =>
                                                    setDiscountToDate(e.target.value)
                                                }
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
                                            onChange={(e) =>
                                                setCouponCode(e.target.value)
                                            }
                                            placeholder="e.g. SUMMER20"
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
                                                step={1}
                                                value={requiredTickets}
                                                onChange={(e) =>
                                                    setRequiredTickets(e.target.value)
                                                }
                                                placeholder="e.g. 2"
                                            />
                                        </label>

                                        <label className="form-field">
                                            <span>Applied tickets</span>
                                            <input
                                                type="number"
                                                min={1}
                                                step={1}
                                                value={appliedTickets}
                                                onChange={(e) =>
                                                    setAppliedTickets(e.target.value)
                                                }
                                                placeholder="e.g. 1"
                                            />
                                        </label>
                                    </>
                                )}
                            </section>
                        </div>

                        {(errorMessage || successMessage) && (
                            <div className="create-event-feedback">
                                {errorMessage && (
                                    <p className="create-event-error">
                                        {errorMessage}
                                    </p>
                                )}
                                {successMessage && (
                                    <p className="create-event-success">
                                        {successMessage}
                                    </p>
                                )}
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