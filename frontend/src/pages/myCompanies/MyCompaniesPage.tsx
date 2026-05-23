import { useEffect, useState } from "react";
import { getCurrentUser, type CurrentUser } from "../../services/currentUserService";
import { getMyCompanies, type CompanyMembership as CompanyMembershipDto } from "../../services/companyService";
import "./MyCompaniesPage.css";

type CompanyStatus = "active" | "suspended" | "closed";

type CompanyMembership = {
    id: string;
    name: string;
    role: string;
    status: CompanyStatus;
};

const COMPANY_DETAILS_BASE_PATH = "/company";
const COMPANY_CREATION_PATH = "/companies/new";

function mapCompanyMembership(dto: CompanyMembershipDto): CompanyMembership {
    const normalizedStatus = dto.status.toLowerCase() as CompanyStatus;

    return {
        id: dto.companyId,
        name: dto.companyName,
        role: dto.role,
        status:
            normalizedStatus === "active" ||
            normalizedStatus === "suspended" ||
            normalizedStatus === "closed"
                ? normalizedStatus
                : "active",
    };
}

function getCompanyStatusLabel(status: CompanyStatus) {
    if (status === "active") {
        return "Active";
    }

    if (status === "suspended") {
        return "Suspended";
    }

    return "Closed";
}

function getCompanyStatusClass(status: CompanyStatus) {
    return `company-status company-status--${status}`;
}

function CompanyCard({ company }: { company: CompanyMembership }) {
    const companyHref = `${COMPANY_DETAILS_BASE_PATH}?companyId=${encodeURIComponent(company.id)}`;

    return (
        <article className="company-card">
            <div className="company-card-main">
                <a className="company-name-link" href={companyHref}>
                    {company.name}
                </a>
                <p className="company-card-meta">Company ID: {company.id}</p>
            </div>

            <div className="company-card-badges">
                <span className="company-role-badge">{company.role}</span>
                <span className={getCompanyStatusClass(company.status)}>
                    {getCompanyStatusLabel(company.status)}
                </span>
            </div>
        </article>
    );
}

function GuestAccessCard() {
    return (
        <section className="empty-state company-access-card">
            <h2>Sign in to view your companies</h2>
            <p>
                This page is available to logged-in members only. Please log in or
                register to continue.
            </p>

            <div className="company-access-actions">
                <a className="company-action-link" href="/login">
                    Log in
                </a>
                <a className="company-action-link company-action-link--secondary" href="/registration">
                    Register
                </a>
            </div>
        </section>
    );
}

function EmptyCompaniesState() {
    return (
        <section className="empty-state company-empty-state">
            <h2>No companies yet</h2>
            <p>
                You are not a part of any company right now. To join one, you need to
                be invited by a company owner or create a new company.
            </p>

            <p className="company-empty-note">
                Create a new company from the button above or visit the company
                creation page when it is available.
            </p>

            <a className="company-action-link" href={COMPANY_CREATION_PATH}>
                Go to company creation
            </a>
        </section>
    );
}

export default function MyCompaniesPage() {
    const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
    const [companies, setCompanies] = useState<CompanyMembership[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        async function loadCompanies() {
            try {
                setIsLoading(true);

                const user = await getCurrentUser();
                setCurrentUser(user);

                if (!user || user.role === "GUEST") {
                    setCompanies([]);
                    return;
                }

                const memberships = await getMyCompanies(user.email);
                setCompanies(memberships.map(mapCompanyMembership));
            } finally {
                setIsLoading(false);
            }
        }

        loadCompanies();
    }, []);

    const isGuest = currentUser === null || currentUser.role === "GUEST";

    return (
        <main className="app-page my-companies-page">
            <section className="page-header">
                <h1>My Companies</h1>
                <p>
                    Review the production companies you belong to, the role you hold in
                    each one, and the current company status.
                </p>
            </section>

            {!isGuest && (
                <section className="company-page-toolbar">
                    <a className="company-create-button" href={COMPANY_CREATION_PATH}>
                        Create company
                    </a>
                </section>
            )}

            {isLoading && (
                <section className="empty-state">
                    <h2>Loading companies...</h2>
                    <p>Please wait while we load the companies available to you.</p>
                </section>
            )}

            {!isLoading && isGuest && <GuestAccessCard />}

            {!isLoading && !isGuest && companies.length === 0 && <EmptyCompaniesState />}

            {!isLoading && !isGuest && companies.length > 0 && (
                <section className="company-list-shell" aria-label="My companies">
                    <div className="company-list-scroll">
                        {companies.map((company) => (
                            <CompanyCard key={company.id} company={company} />
                        ))}
                    </div>
                </section>
            )}
        </main>
    );
}