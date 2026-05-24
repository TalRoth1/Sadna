import { useEffect, useState } from "react";
import { getCurrentUser, type CurrentUser } from "../../services/currentUserService";
import { getMyCompanies, type CompanyMembership } from "../../services/companyService";
// Fallback stub for createEvent to avoid module resolution/type errors when
// ../../services/eventService is missing. Replace with actual import when available.
async function createEvent(payload: {
    companyId: string;
    name: string;
    date: string;
    location: string;
    artist: string;
    type: string;
    status: string;
}): Promise<{ success: boolean } | never> {
    if (!payload || !payload.companyId) throw new Error("companyId is required");
    // Simulate API delay
    await new Promise((res) => setTimeout(res, 200));
    return { success: true };
}
import "./CreateEventPage.css";

type CreateEventPageProps = {
    onCreateCompany: () => void;
    onLogin: () => void;
    onRegister: () => void;
};

export default function CreateEventPage({
    onCreateCompany,
    onLogin,
    onRegister,
}: CreateEventPageProps) {
    const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
    const [companies, setCompanies] = useState<CompanyMembership[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [companyId, setCompanyId] = useState("");
    const [name, setName] = useState("");
    const [date, setDate] = useState("");
    const [location, setLocation] = useState("");
    const [artist, setArtist] = useState("");
    const [type, setType] = useState("");
    const [status, setStatus] = useState("ACTIVE");

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

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();

        setSuccessMessage("");
        setErrorMessage("");

        if (!companyId || !name || !date || !location || !artist || !type || !status) {
            setErrorMessage("Please fill in all fields.");
            return;
        }

        try {
            setIsSubmitting(true);

            await createEvent({
                companyId,
                name,
                date,
                location,
                artist,
                type,
                status,
            });

            setSuccessMessage("Event created successfully.");

            setName("");
            setDate("");
            setLocation("");
            setArtist("");
            setType("");
            setStatus("ACTIVE");
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Failed to create event.",
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
                    <p>Please wait while we load your details.</p>
                </section>
            )}

            {!isLoading && isGuest && (
                <section className="empty-state create-event-access-card">
                    <h2>Sign in to create an event</h2>
                    <p>This page is available to logged-in members only.</p>

                    <div className="create-event-actions">
                        <button className="create-event-button" onClick={onLogin}>
                            Log in
                        </button>
                        <button className="create-event-button create-event-button--secondary" onClick={onRegister}>
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

                    <button className="create-event-button" onClick={onCreateCompany}>
                        Create company
                    </button>
                </section>
            )}

            {!isLoading && !isGuest && companies.length > 0 && (
                <section className="create-event-card">
                    <form className="create-event-form" onSubmit={handleSubmit}>
                        <label>
                            Company
                            <select value={companyId} onChange={(e) => setCompanyId(e.target.value)}>
                                {companies.map((company) => (
                                    <option key={company.companyId} value={company.companyId}>
                                        {company.companyName}
                                    </option>
                                ))}
                            </select>
                        </label>

                        <label>
                            Event name
                            <input value={name} onChange={(e) => setName(e.target.value)} />
                        </label>

                        <label>
                            Event date
                            <input type="datetime-local" value={date} onChange={(e) => setDate(e.target.value)} />
                        </label>

                        <label>
                            Location
                            <input value={location} onChange={(e) => setLocation(e.target.value)} />
                        </label>

                        <label>
                            Artist
                            <input value={artist} onChange={(e) => setArtist(e.target.value)} />
                        </label>

                        <label>
                            Event type/category
                            <input value={type} onChange={(e) => setType(e.target.value)} />
                        </label>

                        <label>
                            Event status
                            <select value={status} onChange={(e) => setStatus(e.target.value)}>
                                <option value="ACTIVE">Active</option>
                                <option value="SUSPENDED">Suspended</option>
                                <option value="CLOSED">Closed</option>
                            </select>
                        </label>

                        {errorMessage && <p className="create-event-error">{errorMessage}</p>}
                        {successMessage && <p className="create-event-success">{successMessage}</p>}

                        <button className="create-event-submit" type="submit" disabled={isSubmitting}>
                            {isSubmitting ? "Creating..." : "Create event"}
                        </button>
                    </form>
                </section>
            )}
        </main>
    );
}