import { useEffect, useState } from "react";
import { getCurrentUser, type CurrentUser } from "../../services/currentUserService";
import "./CompanyCreationPage.css";

type CompanyCreationFormErrors = {
    companyName?: string;
};

type CompanyCreationPageProps = {
    onCreationSuccess: (companyId: string, companyName: string) => void;
};

function getErrorMessage(error: unknown, fallback: string) {
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

    return fallback;
}

function generateCompanyId() {
    if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
        return crypto.randomUUID();
    }

    return `company-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function simulateCreateCompany(companyName: string) {
    return new Promise<{ companyId: string }>((resolve, reject) => {
        window.setTimeout(() => {
            const normalizedName = companyName.trim().toLowerCase();

            if (normalizedName === "error") {
                reject({
                    response: {
                        data: {
                            message: `Failed to create ${companyName.trim()}. Please try again.`,
                        },
                    },
                });
                return;
            }

            resolve({
                companyId: generateCompanyId(),
            });
        }, 700);
    });
}

function GuestAccessCard() {
    return (
        <section className="empty-state company-creation-guest-card">
            <h2>Company creation is for members only</h2>
            <p>
                Please log in with a member account to create a new company.
                Guests cannot access this page.
            </p>

            <div className="company-creation-auth-actions">
                <a className="company-creation-action-link" href="/login">
                    Log in
                </a>
                <a
                    className="company-creation-action-link company-creation-action-link--secondary"
                    href="/registration"
                >
                    Register
                </a>
            </div>
        </section>
    );
}

export default function CompanyCreationPage({
    onCreationSuccess,
}: CompanyCreationPageProps) {
    const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
    const [companyName, setCompanyName] = useState("");
    const [errors, setErrors] = useState<CompanyCreationFormErrors>({});
    const [isLoading, setIsLoading] = useState(true);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        async function loadCurrentUser() {
            try {
                setIsLoading(true);
                const user = await getCurrentUser();
                setCurrentUser(user);
            } finally {
                setIsLoading(false);
            }
        }

        loadCurrentUser();
    }, []);

    const isGuest = currentUser === null || currentUser.role === "GUEST";

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();

        setErrorMessage("");

        const validationErrors: CompanyCreationFormErrors = {};

        if (!companyName.trim()) {
            validationErrors.companyName = "Company name is required.";
        }

        setErrors(validationErrors);

        if (Object.keys(validationErrors).length > 0 || !currentUser) {
            return;
        }

        try {
            setIsSubmitting(true);

            const response = await simulateCreateCompany(
                companyName,
            );

            onCreationSuccess(response.companyId, companyName.trim());
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Company creation failed."));
        } finally {
            setIsSubmitting(false);
        }
    }

    if (isLoading) {
        return (
            <main className="app-page company-creation-page">
                <section className="empty-state">
                    <h2>Loading company creation...</h2>
                    <p>Please wait while we check your account.</p>
                </section>
            </main>
        );
    }

    return (
        <main className="app-page company-creation-page">
            <section className="page-header company-creation-header">
                <p className="company-creation-eyebrow">Member access</p>
                <h1>Company Creation</h1>
                <p>
                    Create a new company from your logged-in account. The system will
                    use your username as the founder automatically.
                </p>
            </section>

            {isGuest ? (
                <GuestAccessCard />
            ) : (
                <div className="company-creation-layout">
                    <section className="company-creation-panel company-creation-summary-card">
                        <h2>Founder details</h2>
                        <p>
                            Your current account will be attached to the company as the
                            founder.
                        </p>

                        <dl className="company-creation-details">
                            <div>
                                <dt>Username</dt>
                                <dd>{currentUser.username}</dd>
                            </div>
                            <div>
                                <dt>Email</dt>
                                <dd>{currentUser.email}</dd>
                            </div>
                        </dl>

                        <p className="company-creation-note">
                            After a successful creation, the site will forward you to the
                            company page!
                        </p>
                    </section>

                    <form className="auth-card company-creation-form" onSubmit={handleSubmit}>
                        <h2>Create a company</h2>
                        <p className="company-creation-form-lead">
                            Tip: Keep the name short and recognizable!
                        </p>

                        <label className="form-field">
                            <span>Company name</span>
                            <input
                                type="text"
                                value={companyName}
                                onChange={(event) => setCompanyName(event.target.value)}
                                placeholder="Enter company name"
                                maxLength={80}
                            />
                            {errors.companyName && <small>{errors.companyName}</small>}
                        </label>

                        {errorMessage && <p className="form-error-message">{errorMessage}</p>}

                        <button type="submit" disabled={isSubmitting}>
                            {isSubmitting ? "Creating company..." : "Create company"}
                        </button>

                        <p className="company-creation-hint">
                            Tip: type <strong>error</strong> to preview the backend error
                            state.
                        </p>
                    </form>
                </div>
            )}
        </main>
    );
}