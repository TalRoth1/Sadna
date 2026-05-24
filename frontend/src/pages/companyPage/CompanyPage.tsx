import { useEffect, useState } from "react";
import {
    deleteEvent,
    getCompanyHierarchy,
    getCompanyPermissions,
    getEventsForUserInCompany,
    inviteCompanyManager,
    inviteCompanyOwner,
    removeCompanyMemberAsOwner,
    type CompanyAccessResponse,
    type CompanyPermissionName as BackendCompanyPermissionName,
    type InviteManagerRequest,
    type InviteOwnerRequest,
} from "../../services/companyService";
import { getCurrentUser, type CurrentUser } from "../../services/currentUserService";
import type { EventSummary } from "../../types/event";
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

type CompanyPageSectionId =
    | "company-overview"
    | "company-permissions"
    | "company-invitations"
    | "company-events"
    | "company-hierarchy";

type CompanyPageSection = {
    id: CompanyPageSectionId;
    label: string;
    isVisible: boolean;
};

type ManagedEvent = EventSummary;

type InvitationTargetRole = "manager" | "owner";

type CompanyPageProps = {
    company: CompanyViewModel;
    onBackToCompanies: () => void;
};

type CompanyPageState = {
    company: CompanyViewModel;
    errorMessage: string;
    isLoading: boolean;
    hierarchyRoots: HierarchyNode[];
    hierarchyErrorMessage: string;
    hierarchySource: string;
    subordinateIds: string[];
};

type HierarchyNode = {
    id: string;
    label: string;
    children: HierarchyNode[];
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

const COMPANY_SECTION_SCROLL_OFFSET_PX = 180;

function buildCompanyPageSections(
    isHierarchyVisible: boolean,
    isInviteComposerVisible: boolean,
): CompanyPageSection[] {
    return [
        { id: "company-overview", label: "Overview", isVisible: true },
        { id: "company-permissions", label: "Permissions", isVisible: true },
        { id: "company-invitations", label: "Invite users", isVisible: isInviteComposerVisible },
        { id: "company-events", label: "My Events", isVisible: true },
        { id: "company-hierarchy", label: "Hierarchy", isVisible: isHierarchyVisible },
    ];
}

function scrollToCompanySection(sectionId: CompanyPageSectionId) {
    const targetElement = document.getElementById(sectionId);

    if (!targetElement) {
        return;
    }

    const targetTop =
        window.scrollY + targetElement.getBoundingClientRect().top - COMPANY_SECTION_SCROLL_OFFSET_PX;

    window.scrollTo({
        top: Math.max(targetTop, 0),
        behavior: "smooth",
    });
}

function scrollToTop() {
    window.scrollTo({
        top: 0,
        behavior: "smooth",
    });
}

function formatEventDate(date: string) {
    const eventDate = new Date(date);

    if (Number.isNaN(eventDate.getTime())) {
        return "Invalid date";
    }

    return eventDate.toLocaleString("en-US", {
        dateStyle: "medium",
        timeStyle: "short",
    });
}

function getManagedEventStatus(event: ManagedEvent) {
    if (event.totalTickets === 0) {
        return "Draft";
    }

    if (event.availableTickets === 0) {
        return "Sold out";
    }

    return "Open";
}

function getManagedEventStatusClass(event: ManagedEvent) {
    return `company-event-status company-event-status--${getManagedEventStatus(event)
        .toLowerCase()
        .replace(/\s+/g, "-")}`;
}

function isHierarchyViewerRole(role: string) {
    const normalizedRole = role.trim().toLowerCase();
    return normalizedRole === "founder" || normalizedRole === "owner";
}

function isCompanyOwnerRole(role: string) {
    const normalizedRole = role.trim().toLowerCase();
    return normalizedRole === "founder" || normalizedRole === "owner";
}

function normalizeMermaidLabel(value: string) {
    const withoutQuotes = value.trim().replace(/^"|"$/g, "");
    // Remove any trailing Perms[...] annotation and normalize newlines
    const cleaned = withoutQuotes
        .replace(/\\n/g, " ")
        .replace(/\s*Perms:?\[[^\]]*\]\s*$/i, "");
    return cleaned.trim();
}

function parseMermaidHierarchy(chart: string): HierarchyNode[] {
    const labelById = new Map<string, string>();
    const childrenById = new Map<string, Set<string>>();
    const allIds = new Set<string>();
    const childIds = new Set<string>();

    const lines = chart
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter((line) => line !== "");

    for (const line of lines) {
        const edgeMatch = line.match(/^([A-Za-z0-9_]+)\s*-->\s*([A-Za-z0-9_]+)$/);
        if (edgeMatch) {
            const parentId = edgeMatch[1];
            const childId = edgeMatch[2];

            allIds.add(parentId);
            allIds.add(childId);
            childIds.add(childId);

            if (!childrenById.has(parentId)) {
                childrenById.set(parentId, new Set<string>());
            }
            childrenById.get(parentId)?.add(childId);
            continue;
        }

        const nodeMatch = line.match(/^([A-Za-z0-9_]+)\[(.+)\]$/);
        if (nodeMatch) {
            const nodeId = nodeMatch[1];
            const rawLabel = nodeMatch[2];

            allIds.add(nodeId);
            labelById.set(nodeId, normalizeMermaidLabel(rawLabel));
        }
    }

    if (allIds.size === 0) {
        return [];
    }

    const rootIds = [...allIds].filter((id) => !childIds.has(id));
    const rootsToBuild = rootIds.length > 0 ? rootIds : [...allIds];

    function buildNode(nodeId: string, visited: Set<string>): HierarchyNode {
        const label = labelById.get(nodeId) ?? nodeId;

        if (visited.has(nodeId)) {
            return { id: nodeId, label, children: [] };
        }

        const nextVisited = new Set(visited);
        nextVisited.add(nodeId);

        const children = [...(childrenById.get(nodeId) ?? new Set<string>())].map((childId) =>
            buildNode(childId, nextVisited),
        );

        return { id: nodeId, label, children };
    }

    return rootsToBuild.map((rootId) => buildNode(rootId, new Set<string>()));
}

function isPermissionGranted(
    companyPermissions: CompanyPermissionName[],
    permission: CompanyPermissionName,
) {
    return companyPermissions.includes(permission);
}

function ManagedEventCard({
    event,
    onDelete,
}: {
    event: ManagedEvent;
    onDelete: (eventId: string, eventName: string) => Promise<void>;
}) {
    return (
        <article className="company-event-card">
            <div className="company-event-card-main">
                <div className="company-event-card-heading">
                    <h3>{event.name}</h3>
                    <span className="company-event-category-badge">{event.type}</span>
                </div>

                <p className="company-event-meta">{formatEventDate(event.date)}</p>
                <p className="company-event-meta">{event.location}</p>
                <p className="company-event-meta company-event-company">
                    Managed directly by the current company
                </p>
            </div>

            <div className="company-event-card-side">
                <span className={getManagedEventStatusClass(event)}>
                    {getManagedEventStatus(event)}
                </span>
                <span className="company-event-rating" aria-label={`Rating ${event.rating}`}>
                    ★ {event.rating.toFixed(1)}
                </span>
                <span className="company-event-availability">
                    {event.availableTickets > 0
                        ? `${event.availableTickets} tickets left`
                        : "No tickets left"}
                </span>

                <button
                    type="button"
                    className="company-event-delete-button"
                    onClick={() => onDelete(event.id, event.name)}
                >
                    Delete event
                </button>
            </div>
        </article>
    );
}

export default function CompanyPage({
    company,
    onBackToCompanies,
}: CompanyPageProps) {
    const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
    const [inviteEmail, setInviteEmail] = useState("");
    const [inviteTargetRole, setInviteTargetRole] = useState<InvitationTargetRole>("manager");
    const [inviteErrorMessage, setInviteErrorMessage] = useState("");
    const [inviteSuccessMessage, setInviteSuccessMessage] = useState("");
    const [isInviteSubmitting, setIsInviteSubmitting] = useState(false);
    const [managedEvents, setManagedEvents] = useState<ManagedEvent[]>([]);
    const [state, setState] = useState<CompanyPageState>({
        company,
        errorMessage: "",
        isLoading: true,
        hierarchyRoots: [],
        hierarchyErrorMessage: "",
        hierarchySource: "",
        subordinateIds: [],
    });

    const isHierarchyVisible = isHierarchyViewerRole(state.company.role);
    const isInviteComposerVisible = isCompanyOwnerRole(state.company.role);
    const [debugShowHierarchyInfo, setDebugShowHierarchyInfo] = useState(false);
    const [debugForceShowRemove, setDebugForceShowRemove] = useState(false);
    const companyPageSections = buildCompanyPageSections(
        isHierarchyVisible,
        isInviteComposerVisible,
    ).filter(
        (section) => section.isVisible,
    );

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

                const mappedCompany = mapAccessToViewModel(access, company);
                let hierarchyRoots: HierarchyNode[] = [];
                let hierarchyErrorMessage = "";
                let hierarchySource = "";
                let managedEventsForUser: ManagedEvent[] = [];

                if (isHierarchyViewerRole(mappedCompany.role)) {
                    try {
                        const hierarchyResponse = await getCompanyHierarchy(company.id, user.email);
                        if (isStale) {
                            return;
                        }

                        hierarchySource = hierarchyResponse.mermaidChart;
                        hierarchyRoots = parseMermaidHierarchy(hierarchyResponse.mermaidChart);
                    } catch (error) {
                        if (isStale) {
                            return;
                        }

                        hierarchyErrorMessage = getErrorMessage(
                            error,
                            "Failed to load the company hierarchy.",
                        );
                    }
                }

                try {
                    managedEventsForUser = await getEventsForUserInCompany(user.email, company.id);
                } catch (error) {
                    if (!isStale) {
                        console.error("Failed to load managed events:", error);
                    }
                }

                // compute subordinate IDs for the current user (descendants of the current user's node)
                let subordinateIds: string[] = [];
                try {
                    if (user && hierarchyRoots.length > 0) {
                        function findNode(nodes: HierarchyNode[], predicate: (n: HierarchyNode) => boolean): HierarchyNode | null {
                            for (const n of nodes) {
                                if (predicate(n)) return n;
                                const found = findNode(n.children, predicate);
                                if (found) return found;
                            }
                            return null;
                        }

                        const userNode = findNode(hierarchyRoots, (n) => {
                            if (!user) return false;
                            return (
                                n.label.includes(user.email) ||
                                (user.username ? n.label.includes(user.username) : false)
                            );
                        });
                        if (userNode) {
                            const ids = new Set<string>();
                            function collectDescendants(n: HierarchyNode) {
                                for (const c of n.children) {
                                    ids.add(c.id);
                                    collectDescendants(c);
                                }
                            }

                            collectDescendants(userNode);
                            subordinateIds = [...ids];
                        }
                    }
                } catch (e) {
                    // ignore subordinate computation errors
                    subordinateIds = [];
                }

                setState({
                    company: mappedCompany,
                    errorMessage: "",
                    isLoading: false,
                    hierarchyRoots,
                    hierarchyErrorMessage,
                    hierarchySource,
                    subordinateIds,
                });
                setManagedEvents(managedEventsForUser);
            } catch (error) {
                if (isStale) {
                    return;
                }

                setState((currentState) => ({
                    ...currentState,
                    isLoading: false,
                    errorMessage: getErrorMessage(error, "Failed to load company permissions."),
                    hierarchyRoots: [],
                    hierarchyErrorMessage: "",
                    hierarchySource: "",
                }));
            }
        }

        loadCompanyAccess();

        return () => {
            isStale = true;
        };
    }, [company]);

    async function handleInviteSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();

        const email = inviteEmail.trim();
        if (email === "") {
            setInviteErrorMessage("Please enter an email address.");
            setInviteSuccessMessage("");
            return;
        }

        if (!currentUser || currentUser.role === "GUEST") {
            setInviteErrorMessage("Please log in again before sending an invitation.");
            setInviteSuccessMessage("");
            return;
        }

        try {
            setIsInviteSubmitting(true);
            setInviteErrorMessage("");
            setInviteSuccessMessage("");

            if (inviteTargetRole === "manager") {
                const managerRequest: InviteManagerRequest = {
                    ownerUsername: currentUser.email,
                    usernameToInvite: email,
                    permissions: [],
                };

                await inviteCompanyManager(state.company.id, managerRequest);
            } else {
                const ownerRequest: InviteOwnerRequest = {
                    ownerUsername: currentUser.email,
                    usernameToInvite: email,
                };

                await inviteCompanyOwner(state.company.id, ownerRequest);
            }

            setInviteEmail("");
            setInviteSuccessMessage("Invitation was sent successfully.");
        } catch (error) {
            setInviteErrorMessage(getErrorMessage(error, "Failed to send the invitation."));
            setInviteSuccessMessage("");
        } finally {
            setIsInviteSubmitting(false);
        }
    }

    async function handleRemoveMember(usernameToRemove: string) {
        if (!currentUser || currentUser.role === "GUEST") {
            window.alert("Please log in again before removing a member.");
            return;
        }

        const confirmed = window.confirm(
            `Remove ${usernameToRemove} from ${state.company.name}? This action cannot be undone.`,
        );

        if (!confirmed) return;

        try {
            await removeCompanyMemberAsOwner(state.company.id, {
                ownerUsername: currentUser.email,
                usernameToRemove,
            });

            // refresh hierarchy
            try {
                const hierarchyResponse = await getCompanyHierarchy(state.company.id, currentUser.email);
                const hierarchySource = hierarchyResponse.mermaidChart;
                const hierarchyRoots = parseMermaidHierarchy(hierarchyResponse.mermaidChart);

                // recompute subordinate ids
                let subordinateIds: string[] = [];
                if (currentUser && hierarchyRoots.length > 0) {
                    function findNode(nodes: HierarchyNode[], predicate: (n: HierarchyNode) => boolean): HierarchyNode | null {
                        for (const n of nodes) {
                            if (predicate(n)) return n;
                            const found = findNode(n.children, predicate);
                            if (found) return found;
                        }
                        return null;
                    }

                    const userNode = findNode(hierarchyRoots, (n) => {
                        if (!currentUser) return false;
                        return (
                            n.label.includes(currentUser.email) ||
                            (currentUser.username ? n.label.includes(currentUser.username) : false)
                        );
                    });
                    if (userNode) {
                        const ids = new Set<string>();
                        function collectDescendants(n: HierarchyNode) {
                            for (const c of n.children) {
                                ids.add(c.id);
                                collectDescendants(c);
                            }
                        }

                        collectDescendants(userNode);
                        subordinateIds = [...ids];
                    }
                }

                setState((s) => ({
                    ...s,
                    hierarchyRoots,
                    hierarchySource,
                    subordinateIds,
                }));
            } catch (err) {
                // if refresh fails, at least notify success of removal
            }

            window.alert("Member removed successfully.");
        } catch (error) {
            window.alert(getErrorMessage(error, "Failed to remove member."));
        }
    }

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

            <nav className="company-section-nav" aria-label="Company page sections">
                {companyPageSections.map((section) => (
                    <button
                        key={section.id}
                        type="button"
                        className="company-section-nav-link"
                        onClick={() => scrollToCompanySection(section.id)}
                    >
                        {section.label}
                    </button>
                ))}
            </nav>

            <section
                id="company-overview"
                className="company-summary-grid"
                aria-label="Company summary"
            >
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

            <section id="company-permissions" className="company-permissions-card">
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

            {isInviteComposerVisible && (
                <section id="company-invitations" className="company-invite-card">
                    <div className="company-invite-card-header">
                        <div>
                            <h2>Invite users</h2>
                            <p>
                                You may send invitations to other users to join this company as a manager or an owner under your supervision.
                            </p>
                        </div>

                        <span className="company-invite-owner-badge">Owner only</span>
                    </div>

                    <form
                        className="company-invite-form"
                        onSubmit={handleInviteSubmit}
                    >
                        <label className="company-invite-field">
                            <span>Email address</span>
                            <input
                                type="email"
                                className="company-invite-input"
                                value={inviteEmail}
                                onChange={(event) => {
                                    setInviteEmail(event.target.value);
                                    setInviteErrorMessage("");
                                    setInviteSuccessMessage("");
                                }}
                                placeholder="user@example.com"
                                autoComplete="email"
                            />
                        </label>

                        <fieldset className="company-invite-role-fieldset">
                            <legend>Invite as</legend>

                            <div className="company-invite-toggle" role="radiogroup" aria-label="Invite as">
                                <button
                                    type="button"
                                    className={
                                        inviteTargetRole === "manager"
                                            ? "company-invite-toggle-option company-invite-toggle-option--active"
                                            : "company-invite-toggle-option"
                                    }
                                    onClick={() => {
                                        setInviteTargetRole("manager");
                                        setInviteErrorMessage("");
                                        setInviteSuccessMessage("");
                                    }}
                                >
                                    Manager
                                </button>
                                <button
                                    type="button"
                                    className={
                                        inviteTargetRole === "owner"
                                            ? "company-invite-toggle-option company-invite-toggle-option--active"
                                            : "company-invite-toggle-option"
                                    }
                                    onClick={() => {
                                        setInviteTargetRole("owner");
                                        setInviteErrorMessage("");
                                        setInviteSuccessMessage("");
                                    }}
                                >
                                    Owner
                                </button>
                            </div>
                        </fieldset>

                        <div className="company-invite-form-actions">
                            <button
                                type="submit"
                                className="company-invite-send-button"
                                disabled={isInviteSubmitting}
                            >
                                {isInviteSubmitting ? "Sending..." : "Send invitation"}
                            </button>
                        </div>

                        {inviteSuccessMessage && (
                            <p className="company-invite-success" role="status">
                                {inviteSuccessMessage}
                            </p>
                        )}

                        {inviteErrorMessage && (
                            <p className="company-invite-error" role="alert">
                                {inviteErrorMessage}
                            </p>
                        )}
                    </form>
                </section>
            )}

            <section id="company-events" className="company-events-card" aria-label="My events">
                <div className="company-events-header">
                    <div>
                        <h2>My Events</h2>
                        <p>
                            Events directly managed by you in {company.name} are listed here. You can manage these events or create new ones.
                        </p>
                    </div>

                    <button
                        type="button"
                        className="company-event-create-button"
                        onClick={() => {
                            window.alert("Create event flow will be wired next.");
                        }}
                    >
                        Create event
                    </button>
                </div>

                <div className="company-event-list">
                    {managedEvents.map((event) => (
                        <ManagedEventCard
                            key={event.id}
                            event={event}
                            onDelete={async (eventId, eventName) => {
                                const shouldDelete = window.confirm(
                                    `Delete event \"${eventName}\"? This cannot be undone.`,
                                );

                                if (!shouldDelete) {
                                    return;
                                }

                                if (!currentUser) {
                                    window.alert("Please log in again before deleting this event.");
                                    return;
                                }

                                try {
                                    await deleteEvent(eventId, {
                                        userEmail: currentUser.email,
                                        eventManagerEmail: currentUser.email,
                                    });

                                    setManagedEvents((currentEvents) =>
                                        currentEvents.filter((currentEvent) => currentEvent.id !== eventId),
                                    );
                                } catch (error) {
                                    window.alert(
                                        getErrorMessage(error, "Failed to delete the event."),
                                    );
                                }
                            }}
                        />
                    ))}

                    {managedEvents.length === 0 && (
                        <section className="empty-state company-empty-events-state">
                            <h2>No managed events yet</h2>
                            <p>
                                You do not have any events managed directly by {company.name} yet.
                            </p>
                        </section>
                    )}
                </div>
            </section>

            {isHierarchyVisible && (
                <section id="company-hierarchy" className="company-hierarchy-card">
                    <h2>Company hierarchy</h2>
                    <p>
                        Team structure for this company based on the current role graph.
                    </p>

                    {state.hierarchyErrorMessage && (
                        <p className="company-hierarchy-error">{state.hierarchyErrorMessage}</p>
                    )}

                    <div className="company-hierarchy-debug-controls">
                        <label className="company-hierarchy-debug-label">
                            <input
                                type="checkbox"
                                checked={debugShowHierarchyInfo}
                                onChange={(e) => setDebugShowHierarchyInfo(e.target.checked)}
                            />
                            Show hierarchy debug info
                        </label>
                        <label className="company-hierarchy-debug-label">
                            <input
                                type="checkbox"
                                checked={debugForceShowRemove}
                                onChange={(e) => setDebugForceShowRemove(e.target.checked)}
                            />
                            Force show Remove buttons
                        </label>
                    </div>

                    {debugShowHierarchyInfo && (
                        <div className="company-hierarchy-debug-panel" aria-live="polite">
                            <h4>Hierarchy debug</h4>
                            <div className="company-hierarchy-debug-row">
                                <strong>Current user:</strong>
                                <span>{currentUser ? `${currentUser.username} (${currentUser.email})` : "none"}</span>
                            </div>
                            <div className="company-hierarchy-debug-row">
                                <strong>Mermaid source:</strong>
                                <pre className="company-hierarchy-debug-pre">{state.hierarchySource || "(none)"}</pre>
                            </div>
                            <div className="company-hierarchy-debug-row">
                                <strong>Parsed roots (JSON):</strong>
                                <pre className="company-hierarchy-debug-pre">{JSON.stringify(state.hierarchyRoots, null, 2)}</pre>
                            </div>
                            <div className="company-hierarchy-debug-row">
                                <strong>Subordinate IDs:</strong>
                                <pre className="company-hierarchy-debug-pre">{JSON.stringify(state.subordinateIds || [], null, 2)}</pre>
                            </div>
                        </div>
                    )}

                    {!state.hierarchyErrorMessage && state.hierarchyRoots.length === 0 && (
                        <p className="company-hierarchy-empty">
                            No hierarchy data is available yet for this company.
                        </p>
                    )}

                    {!state.hierarchyErrorMessage && state.hierarchyRoots.length > 0 && (
                        <div className="company-hierarchy-tree" aria-label="Company hierarchy tree">
                            {(() => {
                                const subordinateSet = new Set<string>(state.subordinateIds || []);
                                const canRemoveMembers = isCompanyOwnerRole(state.company.role) && !!currentUser;

                                return state.hierarchyRoots.map((rootNode) => (
                                    <HierarchyBranch
                                        key={rootNode.id}
                                        node={rootNode}
                                        subordinateIds={subordinateSet}
                                        onRemove={handleRemoveMember}
                                        canRemove={canRemoveMembers}
                                        currentUser={currentUser}
                                        debugShowIds={debugShowHierarchyInfo}
                                        debugForceShowRemove={debugForceShowRemove}
                                    />
                                ));
                            })()}
                        </div>
                    )}

                    {state.hierarchySource && (
                        <details className="company-hierarchy-source">
                            <summary>Mermaid source</summary>
                            <pre>{state.hierarchySource}</pre>
                        </details>
                    )}
                </section>
            )}

            <section className="company-page-actions">
                <button type="button" className="event-filters-reset" onClick={onBackToCompanies}>
                    Back to My Companies
                </button>
            </section>

            <button
                type="button"
                className="company-scroll-top-button"
                onClick={scrollToTop}
                aria-label="Scroll to top of the page"
                title="Scroll to top"
            >
                Scroll up
            </button>
        </main>
    );
}

function HierarchyBranch({
    node,
    subordinateIds,
    onRemove,
    canRemove,
    currentUser,
    debugShowIds,
    debugForceShowRemove,
}: {
    node: HierarchyNode;
    subordinateIds: Set<string>;
    onRemove: (usernameToRemove: string) => Promise<void>;
    canRemove: boolean;
    currentUser: CurrentUser | null;
    debugShowIds?: boolean;
    debugForceShowRemove?: boolean;
}) {
    function extractUsername(label: string) {
        const emailMatch = label.match(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/);
        if (emailMatch) return emailMatch[0];
        // fallback to first token
        return label.split(/\s+/)[0];
    }

    const showRemove = (canRemove && subordinateIds.has(node.id)) || Boolean(debugForceShowRemove);

    return (
        <ul className="company-hierarchy-list">
            <li>
                <div className="company-hierarchy-node">
                    <span>
                        {node.label}
                        {debugShowIds && (
                            <span className="company-hierarchy-node-id">({node.id})</span>
                        )}
                    </span>
                    {showRemove && (
                        <button
                            type="button"
                            className="company-hierarchy-remove-button"
                            onClick={() => onRemove(extractUsername(node.label))}
                        >
                            Remove from company
                        </button>
                    )}
                </div>
                {node.children.length > 0 && (
                    <div className="company-hierarchy-children">
                        {node.children.map((childNode) => (
                            <HierarchyBranch
                                key={childNode.id}
                                node={childNode}
                                subordinateIds={subordinateIds}
                                onRemove={onRemove}
                                canRemove={canRemove}
                                currentUser={currentUser}
                            />
                        ))}
                    </div>
                )}
            </li>
        </ul>
    );
}