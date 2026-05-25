import { useEffect, useState } from "react";
import { getCurrentUser, type CurrentUser } from "../../services/currentUserService";
import {
    acceptInvitation,
    getMyCompanies,
    getMyInvitations,
    type CompanyInvitationResponse,
    type CompanyMembership as CompanyMembershipDto,
    rejectInvitation,
} from "../../services/companyService";
import "./MyCompaniesPage.css";

type CompanyStatus = "active" | "suspended" | "closed";

type CompanyMembership = {
    id: string;
    name: string;
    role: string;
    status: CompanyStatus;
};

type CompanyInvitationRole = "Owner" | "Manager";

type CompanyInvitation = {
    id: string;
    companyName: string;
    companyId: string;
    inviterName: string;
    role: CompanyInvitationRole;
    invitationType: string;
    permissions: string[] | null;
};

type CompanyPermissionName =
    | "Manage inventory"
    | "Configure layout"
    | "Manage policies"
    | "Customer service"
    | "View history"
    | "Generate sales reports";

type MyCompaniesPageProps = {
    onCreateCompany: () => void;
    onOpenCompany: (
        companyId: string,
        companyName: string,
        role: string,
        status: CompanyStatus,
        permissions: CompanyPermissionName[],
    ) => void;
};

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

function getPermissionsForRole(role: string): CompanyPermissionName[] {
    const normalizedRole = role.trim().toLowerCase();

    if (normalizedRole === "manager") {
        return [
            "Manage inventory",
            "Customer service",
            "View history",
        ];
    }

    return [
        "Manage inventory",
        "Configure layout",
        "Manage policies",
        "Customer service",
        "View history",
        "Generate sales reports",
    ];
}

function CompanyCard({
    company,
    onOpenCompany,
}: {
    company: CompanyMembership;
    onOpenCompany: (
        companyId: string,
        companyName: string,
        role: string,
        status: CompanyStatus,
        permissions: CompanyPermissionName[],
    ) => void;
}) {
    return (
        <article className="company-card">
            <div className="company-card-main">
                <button
                    type="button"
                    className="company-name-link"
                    onClick={() =>
                        onOpenCompany(
                            company.id,
                            company.name,
                            company.role,
                            company.status,
                            getPermissionsForRole(company.role),
                        )
                    }
                >
                    {company.name}
                </button>
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

function EmptyCompaniesState({ onCreateCompany }: { onCreateCompany: () => void }) {
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

            <button type="button" className="company-action-link" onClick={onCreateCompany}>
                Go to company creation
            </button>
        </section>
    );
}

function mapInvitation(dto: CompanyInvitationResponse): CompanyInvitation {
    return {
        id: dto.invitationId,
        companyName: dto.companyName,
        companyId: dto.companyId,
        inviterName: dto.appointerUsername,
        role: dto.invitationType === "MANAGER" ? "Manager" : "Owner",
        invitationType: dto.invitationType === "MANAGER" ? "Manager invitation" : "Owner invitation",
        permissions: dto.permissions,
    };
}

function InvitationCard({
    invitation,
    onAccept,
    onReject,
}: {
    invitation: CompanyInvitation;
    onAccept: (invitationId: string) => void;
    onReject: (invitationId: string) => void;
}) {
    return (
        <article className="company-invitation-card">
            <div className="company-invitation-card-main">
                <h3>{invitation.companyName}</h3>
                <p className="company-invitation-card-meta">Invited by {invitation.inviterName}</p>
                <p className="company-invitation-card-meta">Role offered: {invitation.role}</p>
                <p className="company-invitation-card-meta company-invitation-card-type">
                    Invitation type: {invitation.invitationType}
                </p>
                {invitation.permissions && invitation.permissions.length > 0 && (
                    <p className="company-invitation-card-meta">
                        Permissions: {invitation.permissions.join(", ")}
                    </p>
                )}
            </div>

            <div className="company-invitation-actions">
                <button
                    type="button"
                    className="company-invitation-button company-invitation-button--accept"
                    onClick={() => onAccept(invitation.id)}
                >
                    Accept
                </button>
                <button
                    type="button"
                    className="company-invitation-button company-invitation-button--reject"
                    onClick={() => onReject(invitation.id)}
                >
                    Reject
                </button>
            </div>
        </article>
    );
}

export default function MyCompaniesPage({
    onCreateCompany,
    onOpenCompany,
}: MyCompaniesPageProps) {
    const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
    const [companies, setCompanies] = useState<CompanyMembership[]>([]);
    const [invitations, setInvitations] = useState<CompanyInvitation[]>([]);
    const [isLoading, setIsLoading] = useState(true);

    async function reloadDataForUser(userEmail: string) {
        const memberships = await getMyCompanies(userEmail);
        setCompanies(memberships.map(mapCompanyMembership));

        try {
            const invitationDtos = await getMyInvitations(userEmail);
            setInvitations(invitationDtos.map(mapInvitation));
        } catch (error) {
            console.error("Failed to load company invitations:", error);
            setInvitations([]);
        }
    }

    useEffect(() => {
        async function loadCompanies() {
            try {
                setIsLoading(true);

                const user = await getCurrentUser();
                setCurrentUser(user);

                if (!user || user.role === "GUEST") {
                    setCompanies([]);
                    setInvitations([]);
                    return;
                }

                await reloadDataForUser(user.email);
            } finally {
                setIsLoading(false);
            }
        }

        loadCompanies();
    }, []);

    const isGuest = currentUser === null || currentUser.role === "GUEST";

    async function handleInvitationAction(
        invitationId: string,
        companyId: string,
        action: "accept" | "reject",
    ) {
        if (!currentUser || currentUser.role === "GUEST") {
            window.alert("Please sign in again before handling invitations.");
            return;
        }

        try {
            if (action === "accept") {
                await acceptInvitation(companyId, invitationId, currentUser.email);
            } else {
                await rejectInvitation(companyId, invitationId, currentUser.email);
            }

            await reloadDataForUser(currentUser.email);
        } catch (error) {
            console.error(`Failed to ${action} invitation:`, error);
            window.alert(`Failed to ${action} the invitation. Please try again.`);
        }
    }

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
                    <button type="button" className="company-create-button" onClick={onCreateCompany}>
                        Create company
                    </button>
                </section>
            )}

            {isLoading && (
                <section className="empty-state">
                    <h2>Loading companies...</h2>
                    <p>Please wait while we load the companies available to you.</p>
                </section>
            )}

            {!isLoading && isGuest && <GuestAccessCard />}

            {!isLoading && !isGuest && companies.length === 0 && (
                <EmptyCompaniesState onCreateCompany={onCreateCompany} />
            )}

            {!isLoading && !isGuest && companies.length > 0 && (
                <section className="company-list-shell" aria-label="My companies">
                    <div className="company-list-scroll">
                        {companies.map((company) => (
                            <CompanyCard
                                key={company.id}
                                company={company}
                                onOpenCompany={onOpenCompany}
                            />
                        ))}
                    </div>
                </section>
            )}

            {!isLoading && !isGuest && (
                <section className="company-invitations-shell" aria-label="Pending company invitations">
                    <div className="company-invitations-header">
                        <div>
                            <h2>Pending invitations</h2>
                            <p>
                                Companies that invited you will appear here. You can accept or reject
                                each invitation from this section.
                            </p>
                        </div>
                    </div>

                    <div className="company-invitations-list">
                        {invitations.map((invitation) => (
                            <InvitationCard
                                key={invitation.id}
                                invitation={invitation}
                                onAccept={(invitationId) => {
                                    void handleInvitationAction(
                                        invitationId,
                                        invitation.companyId,
                                        "accept",
                                    );
                                }}
                                onReject={(invitationId) => {
                                    void handleInvitationAction(
                                        invitationId,
                                        invitation.companyId,
                                        "reject",
                                    );
                                }}
                            />
                        ))}

                        {invitations.length === 0 && (
                            <section className="empty-state company-empty-invitations-state">
                                <h2>No pending invitations</h2>
                                <p>
                                    When a company invites you, the invitation cards will show up here.
                                </p>
                            </section>
                        )}
                    </div>
                </section>
            )}
        </main>
    );
}