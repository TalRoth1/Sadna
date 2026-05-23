import { useEffect, useState } from "react";
import { getCompanyPermissions, type CompanyAccessResponse, type CompanyPermissionName as BackendCompanyPermissionName } from "../../services/companyService";
import { getCurrentUser, type CurrentUser } from "../../services/currentUserService";
import "./CompanyPage.css";

type CompanyStatus = "Active" | "Suspended" | "Closed";

type CompanyPermissionName =
    | "Manage inventory"
    | "Configure layout"
    | "Manage policies"
    | "Customer service"
    | "View history"
    | "Generate sales reports";

const ALL_PERMISSIONS: CompanyPermissionName[] = [
    "Manage inventory",
    "Configure layout",
    "Manage policies",
    "Customer service",
    "View history",
    "Generate sales reports",
];

type CompanyViewModel = {
    id: string;
    name: string;
    role: string;
    status: CompanyStatus;
    permissions: CompanyPermissionName[];
};

type CompanyPageProps = {
    company: CompanyViewModel;
    onBackToCompanies: () => void;
};

type CompanyPageState = {
    company: CompanyViewModel;
    errorMessage: string;
    isLoading: boolean;
};

function getPermissionLabel(permission: BackendCompanyPermissionName): CompanyPermissionName {
    switch (permission) {
        case "MANAGE_INVENTORY":
            return "Manage inventory";
        case "CONFIGURE_LAYOUT":
            return "Configure layout";
        case "MANAGE_POLICIES":
            return "Manage policies";
        case "CUSTOMER_SERVICE":
            return "Customer service";
        case "VIEW_HISTORY":
            return "View history";
        case "REPORTS_GENERATION":
            return "Generate sales reports";
    }
}

function getStatusLabel(status: string): CompanyStatus {
    const normalizedStatus = status.trim().toLowerCase();

    if (normalizedStatus === "suspended") {
        return "Suspended";
    }

    if (normalizedStatus === "closed") {
        return "Closed";
    }

    return "Active";
}

function mapAccessToViewModel(
    access: CompanyAccessResponse,
    fallbackCompany: CompanyViewModel,
): CompanyViewModel {
    return {
        id: access.companyId,
        name: access.companyName || fallbackCompany.name,
        role: access.role,
        status: getStatusLabel(access.status),
        permissions: access.grantedPermissions.map(getPermissionLabel),
    };
}

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

function getStatusClass(status: CompanyStatus) {
    return `company-status company-status--${status.toLowerCase()}`;
}

function isPermissionGranted(
    companyPermissions: CompanyPermissionName[],
    permission: CompanyPermissionName,
) {
    return companyPermissions.includes(permission);
}

export default function CompanyPage({
    company,
    onBackToCompanies,
}: CompanyPageProps) {
    const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
    const [state, setState] = useState<CompanyPageState>({
        company,
        errorMessage: "",
        isLoading: true,
    });

    useEffect(() => {
        let isStale = false;

        async function loadCompanyAccess() {
            try {
                setState((currentState) => ({ ...currentState, isLoading: true, errorMessage: "" }));

                const user = await getCurrentUser();
                if (isStale) {
                    return;
                }

                setCurrentUser(user);

                if (!user || user.role === "GUEST") {
                    setState((currentState) => ({
                        ...currentState,
                        isLoading: false,
                        errorMessage: "Please log in to view this company.",
                    }));
                    return;
                }

                const access = await getCompanyPermissions(company.id, user.email);
                if (isStale) {
                    return;
                }

                setState({
                    company: mapAccessToViewModel(access, company),
                    errorMessage: "",
                    isLoading: false,
                });
            } catch (error) {
                if (isStale) {
                    return;
                }

                setState((currentState) => ({
                    ...currentState,
                    isLoading: false,
                    errorMessage: getErrorMessage(error, "Failed to load company permissions."),
                }));
            }
        }

        loadCompanyAccess();

        return () => {
            isStale = true;
        };
    }, [company]);

    if (state.isLoading) {
        return (
            <main className="app-page company-page">
                <section className="empty-state">
                    <h2>Loading company...</h2>
                    <p>Please wait while we load the current company access data.</p>
                </section>
            </main>
        );
    }

    if (state.errorMessage && (!currentUser || currentUser.role === "GUEST")) {
        return (
            <main className="app-page company-page">
                <section className="empty-state company-access-card">
                    <h2>Sign in to view this company</h2>
                    <p>{state.errorMessage}</p>

                    <div className="company-access-actions">
                        <a className="company-action-link" href="/login">
                            Log in
                        </a>
                        <a className="company-action-link company-action-link--secondary" href="/registration">
                            Register
                        </a>
                    </div>
                </section>
            </main>
        );
    }

    return (
        <main className="app-page company-page">
            <section className="page-header company-page-header">
                <h1>{state.company.name}</h1>
                <p>Company ID: {state.company.id}</p>
            </section>

            <section className="company-summary-grid" aria-label="Company summary">
                <article className="company-summary-card">
                    <span className="company-summary-label">My Role:</span>
                    <strong className="company-summary-value">{state.company.role}</strong>
                </article>

                <article className="company-summary-card">
                    <span className="company-summary-label">Status</span>
                    <strong className={getStatusClass(state.company.status)}>
                        {state.company.status}
                    </strong>
                </article>
            </section>

            <section className="company-permissions-card">
                <h2>Current permissions</h2>
                <p>
                    Each permission below is shown for the selected company role.
                    Granted permissions are green and unavailable permissions are red.
                </p>

                <div className="company-permissions-list">
                    {ALL_PERMISSIONS.map((permission) => (
                        <span
                            key={permission}
                            className={
                                isPermissionGranted(state.company.permissions, permission)
                                    ? "company-permission-chip company-permission-chip--granted"
                                    : "company-permission-chip company-permission-chip--denied"
                            }
                        >
                            {permission}
                        </span>
                    ))}
                </div>
            </section>

            <section className="company-page-actions">
                <button type="button" className="event-filters-reset" onClick={onBackToCompanies}>
                    Back to My Companies
                </button>
            </section>
        </main>
    );
}