import { useEffect, useState } from "react";
import type { ComponentProps, FormEvent } from "react";
import {
    changeManagerPermissions,
    deleteEvent,
    deletePolicyRule,
    getCompanyHierarchy,
    getCompanyPermissions,
    getCompanyPolicies,
    getEventsForUserInCompany,
    inviteCompanyManager,
    inviteCompanyOwner,
    removeDiscount,
    removeCompanyMemberAsOwner,
    type CompanyAccessResponse,
    type CompanyPoliciesResponse as BackendCompanyPoliciesResponse,
    type CompanyPermissionName as BackendCompanyPermissionName,
    type ChangeManagerPermissionsRequest,
    type InviteManagerRequest,
    type InviteOwnerRequest,
} from "../../services/companyService";
import { getCurrentUser, type CurrentUser } from "../../services/currentUserService";
import { drawLotteryWinners, startRegularSale } from "../../services/lotteryService";
import type { EventSummary } from "../../types/event";
import CompanyPoliciesSection from "./CompanyPoliciesSection";
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

const ALL_BACKEND_PERMISSIONS: BackendCompanyPermissionName[] = [
    "MANAGE_INVENTORY",
    "CONFIGURE_LAYOUT",
    "MANAGE_POLICIES",
    "CUSTOMER_SERVICE",
    "VIEW_HISTORY",
    "REPORTS_GENERATION",
];

type CompanyViewModel = {
    id: string;
    name: string;
    role: string;
    status: CompanyStatus;
    permissions: CompanyPermissionName[];
};

type CompanyPoliciesSectionProps = ComponentProps<typeof CompanyPoliciesSection>;

type CompanyPoliciesViewModel = {
    purchaseRules: NonNullable<CompanyPoliciesSectionProps["purchaseRules"]>;
    discountRules: NonNullable<CompanyPoliciesSectionProps["discountRules"]>;
};

type CompanyPageSectionId =
    | "company-overview"
    | "company-permissions"
    | "company-policies"
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
    onCreateEvent: (companyId: string) => void;
    onSelectEvent: (eventId: string) => void;
    onEditEvent: (eventId: string) => void;
};

type CompanyPageState = {
    company: CompanyViewModel;
    errorMessage: string;
    isLoading: boolean;
    companyPolicies: CompanyPoliciesViewModel | null;
    hierarchyRoots: HierarchyNode[];
    hierarchyErrorMessage: string;
    hierarchySource: string;
    subordinateIds: string[];
    managerPermissionsByNodeId: Record<string, BackendCompanyPermissionName[]>;
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

function mapCompanyPurchaseRules(
    rules: BackendCompanyPoliciesResponse["purchasePolicy"]["rules"],
): CompanyPoliciesViewModel["purchaseRules"] {
    return rules.map((rule) => {
        switch (rule.kind) {
            case "AGE":
                return {
                    id: rule.id,
                    kind: "AGE",
                    minAge: rule.minAge ?? 0,
                };
            case "MIN_TICKETS":
                return {
                    id: rule.id,
                    kind: "MIN_TICKETS",
                    minTickets: rule.minTickets ?? 0,
                };
            case "MAX_TICKETS":
                return {
                    id: rule.id,
                    kind: "MAX_TICKETS",
                    maxTickets: rule.maxTickets ?? 0,
                };
            case "LONE_SEAT":
                return {
                    id: rule.id,
                    kind: "LONE_SEAT",
                    allowLoneSeat: rule.allowLoneSeat ?? false,
                };
        }
    });
}

function mapCompanyDiscountRules(
    rules: BackendCompanyPoliciesResponse["discountPolicy"]["rules"],
): CompanyPoliciesViewModel["discountRules"] {
    return rules.map((rule) => {
        switch (rule.kind) {
            case "OVERT":
                return {
                    id: rule.id,
                    kind: "OVERT",
                    percent: rule.percent ?? 0,
                    fromDate: rule.fromDate,
                    toDate: rule.toDate,
                };
            case "CONDITIONAL":
                return {
                    id: rule.id,
                    kind: "CONDITIONAL",
                    percent: rule.percent ?? 0,
                    fromDate: rule.fromDate,
                    toDate: rule.toDate,
                    requiredTickets: rule.requiredTickets ?? 0,
                    appliedTickets: rule.appliedTickets ?? 0,
                };
            case "COUPON":
                return {
                    id: rule.id,
                    kind: "COUPON",
                    percent: rule.percent ?? 0,
                    fromDate: rule.fromDate,
                    toDate: rule.toDate,
                    code: rule.code ?? "",
                };
        }
    });
}

function mapCompanyPolicies(response: BackendCompanyPoliciesResponse): CompanyPoliciesViewModel {
    return {
        purchaseRules: mapCompanyPurchaseRules(response.purchasePolicy.rules),
        discountRules: mapCompanyDiscountRules(response.discountPolicy.rules),
    };
}

const COMPANY_SECTION_SCROLL_OFFSET_PX = 180;

function buildCompanyPageSections(
    isHierarchyVisible: boolean,
    isInviteComposerVisible: boolean,
    isPoliciesVisible: boolean,
): CompanyPageSection[] {
    return [
        { id: "company-overview", label: "Overview", isVisible: true },
        { id: "company-permissions", label: "Permissions", isVisible: true },
        { id: "company-policies", label: "Policies", isVisible: isPoliciesVisible },
        { id: "company-invitations", label: "Invite users", isVisible: isInviteComposerVisible },
        { id: "company-events", label: "My Events", isVisible: true },
        { id: "company-hierarchy", label: "Hierarchy", isVisible: isHierarchyVisible },
    ];
}

function getDrawWinnersErrorMessage(error: unknown) {
    const rawMessage =
        error instanceof Error
            ? error.message
            : getErrorMessage(error, "Failed to draw winners.");

    const normalizedMessage = rawMessage.toLowerCase();

    if (
        normalizedMessage.includes("winners have already been drawn") ||
        normalizedMessage.includes("already been drawn")
    ) {
        return "Winners have already been drawn for this lottery. You cannot draw winners again.";
    }

    return rawMessage;
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
    const cleaned = withoutQuotes
        .replace(/\\n/g, " ")
        .replace(/\s*Perms:?\[[^\]]*\]\s*$/i, "");
    return cleaned.trim();
}

function isManagerHierarchyLabel(label: string) {
    return /\(Manager\)$/i.test(label.trim());
}

function parseManagerPermissionsFromMermaid(chart: string) {
    const permissionsByNodeId: Record<string, BackendCompanyPermissionName[]> = {};

    const lines = chart
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter((line) => line !== "");

    for (const line of lines) {
        const nodeMatch = line.match(/^([A-Za-z0-9_]+)\[(.+)\]$/);
        if (!nodeMatch) {
            continue;
        }

        const nodeId = nodeMatch[1];
        const rawLabel = nodeMatch[2];
        const permissionsMatch = rawLabel.match(/Perms:?\[([^\]]*)\]/i);

        if (!permissionsMatch) {
            continue;
        }

        const permissions = permissionsMatch[1]
            .split(",")
            .map((permission) => permission.trim())
            .filter((permission): permission is BackendCompanyPermissionName =>
                ALL_BACKEND_PERMISSIONS.includes(permission as BackendCompanyPermissionName),
            );

        permissionsByNodeId[nodeId] = permissions;
    }

    return permissionsByNodeId;
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

function isLotteryEvent(event: ManagedEvent) {
    const rawEvent = event as any;

    const eventType = String(
        rawEvent.type ??
            rawEvent.eventType ??
            rawEvent.saleMode ??
            "",
    )
        .trim()
        .toLowerCase();

    return (
        eventType === "lottery" ||
        eventType === "הגרלה" ||
        eventType.includes("lottery") ||
        Boolean(rawEvent.lotteryId)
    );
}

function ManagedEventCard({
    event,
    onOpenEvent,
    onEditEvent,
    onDelete,
    onStartRegularSale,
    isStartingRegularSale,
}: {
    event: ManagedEvent;
    onOpenEvent: (eventId: string) => void;
    onEditEvent: (eventId: string) => void;
    onDelete: (eventId: string, eventName: string) => Promise<void>;
    onStartRegularSale: (eventId: string, eventName: string) => Promise<void>;
    isStartingRegularSale: boolean;
}) {
    const isLottery = isLotteryEvent(event);

    return (
        <article className="company-event-card">
            <div className="company-event-card-main">
                <div className="company-event-card-heading">
                    <button
                        type="button"
                        className="company-event-title-button"
                        onClick={() => onOpenEvent(event.id)}
                    >
                        {event.name}
                    </button>
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

                {isLottery && <LotteryDrawButton eventId={event.id} />}

                {isLottery && (
                    <button
                        type="button"
                        className="company-event-start-sale-button"
                        disabled={isStartingRegularSale}
                        onClick={() => onStartRegularSale(event.id, event.name)}
                    >
                        {isStartingRegularSale
                            ? "Starting sale..."
                            : "Start regular sale"}
                    </button>
                )}

                <button
                    type="button"
                    className="company-event-edit-button"
                    onClick={() => onEditEvent(event.id)}
                >
                    Edit event
                </button>

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

function LotteryDrawButton({ eventId }: { eventId: string }) {
    const [isLoading, setIsLoading] = useState(false);

    async function handleDraw() {
        if (!window.confirm("Draw winners for this lottery? This action cannot be undone.")) {
            return;
        }

        try {
            setIsLoading(true);

            const expiry = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
            const winners = await drawLotteryWinners(eventId, expiry);

            const winnerCount = Object.keys(winners || {}).length;
            window.alert(`Winners were drawn successfully. ${winnerCount} winner(s).`);
            console.log("Lottery winners:", winners);
        } catch (error) {
            const message = getDrawWinnersErrorMessage(error);
            window.alert(message);
        } finally {
            setIsLoading(false);
        }
    }

    return (
        <button
            type="button"
            className="company-event-draw-button"
            onClick={handleDraw}
            disabled={isLoading}
            style={{ marginBottom: 8 }}
        >
            {isLoading ? "Drawing..." : "Draw winners"}
        </button>
    );
}

export default function CompanyPage({
    company,
    onBackToCompanies,
    onCreateEvent,
    onSelectEvent,
    onEditEvent,
}: CompanyPageProps) {
    const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
    const [inviteEmail, setInviteEmail] = useState("");
    const [inviteTargetRole, setInviteTargetRole] = useState<InvitationTargetRole>("manager");
    const [inviteErrorMessage, setInviteErrorMessage] = useState("");
    const [inviteSuccessMessage, setInviteSuccessMessage] = useState("");
    const [isInviteSubmitting, setIsInviteSubmitting] = useState(false);
    const [managedEvents, setManagedEvents] = useState<ManagedEvent[]>([]);
    const [isPermissionsModalOpen, setIsPermissionsModalOpen] = useState(false);
    const [startingRegularSaleEventId, setStartingRegularSaleEventId] =
        useState<string | null>(null);
    const [selectedManagerNode, setSelectedManagerNode] = useState<HierarchyNode | null>(null);
    const [permissionDraft, setPermissionDraft] = useState<BackendCompanyPermissionName[]>([]);

    const [state, setState] = useState<CompanyPageState>({
        company,
        errorMessage: "",
        isLoading: true,
        companyPolicies: null,
        hierarchyRoots: [],
        hierarchyErrorMessage: "",
        hierarchySource: "",
        subordinateIds: [],
        managerPermissionsByNodeId: {},
    });

    const isHierarchyVisible = isHierarchyViewerRole(state.company.role);
    const isInviteComposerVisible = isCompanyOwnerRole(state.company.role);
    const isPoliciesVisible = state.company.permissions.includes("Manage policies");
    const companyPageSections = buildCompanyPageSections(
        isHierarchyVisible,
        isInviteComposerVisible,
        isPoliciesVisible,
    ).filter((section) => section.isVisible);

    useEffect(() => {
        let isStale = false;

        async function loadCompanyAccess() {
            try {
                setState((currentState) => ({
                    ...currentState,
                    isLoading: true,
                    errorMessage: "",
                }));

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
                let companyPolicies: CompanyPoliciesViewModel | null = null;
                let managerPermissionsByNodeId: Record<string, BackendCompanyPermissionName[]> = {};
                let managedEventsForUser: ManagedEvent[] = [];

                if (mappedCompany.permissions.includes("Manage policies")) {
                    try {
                        const policiesResponse = await getCompanyPolicies(company.id, user.email);

                        if (isStale) {
                            return;
                        }

                        companyPolicies = mapCompanyPolicies(policiesResponse);
                    } catch (error) {
                        if (!isStale) {
                            console.error("Failed to load company policies:", error);
                        }
                    }
                }

                if (isHierarchyViewerRole(mappedCompany.role)) {
                    try {
                        const hierarchyResponse = await getCompanyHierarchy(company.id, user.email);

                        if (isStale) {
                            return;
                        }

                        hierarchySource = hierarchyResponse.mermaidChart;
                        hierarchyRoots = parseMermaidHierarchy(hierarchyResponse.mermaidChart);
                        managerPermissionsByNodeId = parseManagerPermissionsFromMermaid(
                            hierarchyResponse.mermaidChart,
                        );
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

                let subordinateIds: string[] = [];

                try {
                    if (user && hierarchyRoots.length > 0) {
                        function findNode(
                            nodes: HierarchyNode[],
                            predicate: (node: HierarchyNode) => boolean,
                        ): HierarchyNode | null {
                            for (const node of nodes) {
                                if (predicate(node)) {
                                    return node;
                                }

                                const found = findNode(node.children, predicate);

                                if (found) {
                                    return found;
                                }
                            }

                            return null;
                        }

                        const userNode = findNode(hierarchyRoots, (node) => {
                            if (!user) {
                                return false;
                            }

                            return (
                                node.label.includes(user.email) ||
                                (user.username ? node.label.includes(user.username) : false)
                            );
                        });

                        if (userNode) {
                            const ids = new Set<string>();

                            function collectDescendants(node: HierarchyNode) {
                                for (const child of node.children) {
                                    ids.add(child.id);
                                    collectDescendants(child);
                                }
                            }

                            collectDescendants(userNode);
                            subordinateIds = [...ids];
                        }
                    }
                } catch {
                    subordinateIds = [];
                }

                setState({
                    company: mappedCompany,
                    errorMessage: "",
                    isLoading: false,
                    companyPolicies,
                    hierarchyRoots,
                    hierarchyErrorMessage,
                    hierarchySource,
                    subordinateIds,
                    managerPermissionsByNodeId,
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
                    companyPolicies: null,
                    hierarchyRoots: [],
                    hierarchyErrorMessage: "",
                    hierarchySource: "",
                    subordinateIds: [],
                    managerPermissionsByNodeId: {},
                }));
            }
        }

        loadCompanyAccess();

        return () => {
            isStale = true;
        };
    }, [company]);

    async function handleStartRegularSale(eventId: string, eventName: string) {
        const confirmed = window.confirm(
            `Start regular sale for "${eventName}"? After this, users will be able to buy tickets normally without a lottery code.`,
        );

        if (!confirmed) {
            return;
        }

        try {
            setStartingRegularSaleEventId(eventId);

            await startRegularSale(eventId);

            window.alert("Regular sale started successfully.");

            setManagedEvents((currentEvents) =>
                currentEvents.map((managedEvent) =>
                    managedEvent.id === eventId
                        ? {
                              ...managedEvent,
                              type: "Regular Sale",
                          }
                        : managedEvent,
                ),
            );
        } catch (error) {
            window.alert(
                getErrorMessage(error, "Failed to start regular sale."),
            );
        } finally {
            setStartingRegularSaleEventId(null);
        }
    }

    async function handleInviteSubmit(event: FormEvent<HTMLFormElement>) {
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

    async function handleRemoveMember(emailToRemove: string) {
        if (!currentUser || currentUser.role === "GUEST") {
            window.alert("Please log in again before removing a member.");
            return;
        }

        const confirmed = window.confirm(
            `Remove ${emailToRemove} from ${state.company.name}? This action cannot be undone.`,
        );

        if (!confirmed) {
            return;
        }

        try {
            await removeCompanyMemberAsOwner(state.company.id, {
                ownerEmail: currentUser.email,
                emailToRemove,
            });

            try {
                const hierarchyResponse = await getCompanyHierarchy(state.company.id, currentUser.email);
                const hierarchySource = hierarchyResponse.mermaidChart;
                const hierarchyRoots = parseMermaidHierarchy(hierarchyResponse.mermaidChart);
                const managerPermissionsByNodeId = parseManagerPermissionsFromMermaid(
                    hierarchyResponse.mermaidChart,
                );

                let subordinateIds: string[] = [];

                if (currentUser && hierarchyRoots.length > 0) {
                    function findNode(
                        nodes: HierarchyNode[],
                        predicate: (node: HierarchyNode) => boolean,
                    ): HierarchyNode | null {
                        for (const node of nodes) {
                            if (predicate(node)) {
                                return node;
                            }

                            const found = findNode(node.children, predicate);

                            if (found) {
                                return found;
                            }
                        }

                        return null;
                    }

                    const userNode = findNode(hierarchyRoots, (node) => {
                        if (!currentUser) {
                            return false;
                        }

                        return (
                            node.label.includes(currentUser.email) ||
                            (currentUser.username ? node.label.includes(currentUser.username) : false)
                        );
                    });

                    if (userNode) {
                        const ids = new Set<string>();

                        function collectDescendants(node: HierarchyNode) {
                            for (const child of node.children) {
                                ids.add(child.id);
                                collectDescendants(child);
                            }
                        }

                        collectDescendants(userNode);
                        subordinateIds = [...ids];
                    }
                }

                setState((currentState) => ({
                    ...currentState,
                    hierarchyRoots,
                    hierarchySource,
                    subordinateIds,
                    managerPermissionsByNodeId,
                }));
            } catch {
                // If refresh fails, the member removal itself still succeeded.
            }

            window.alert("Member removed successfully.");
        } catch (error) {
            window.alert(getErrorMessage(error, "Failed to remove member."));
        }
    }

    function openPermissionsModal(node: HierarchyNode) {
        setSelectedManagerNode(node);
        setPermissionDraft(state.managerPermissionsByNodeId[node.id] ?? []);
        setIsPermissionsModalOpen(true);
    }

    function closePermissionsModal() {
        setIsPermissionsModalOpen(false);
        setSelectedManagerNode(null);
        setPermissionDraft([]);
    }

    function togglePermissionDraft(permission: BackendCompanyPermissionName) {
        setPermissionDraft((currentPermissions) =>
            currentPermissions.includes(permission)
                ? currentPermissions.filter((currentPermission) => currentPermission !== permission)
                : [...currentPermissions, permission],
        );
    }

    async function refreshCompanyPolicies() {
        if (!currentUser || !isPoliciesVisible) {
            return;
        }

        const policiesResponse = await getCompanyPolicies(state.company.id, currentUser.email);

        setState((currentState) => ({
            ...currentState,
            companyPolicies: mapCompanyPolicies(policiesResponse),
        }));
    }

    async function handleRemovePurchasePolicyRule(ruleId: string, ruleLabel: string) {
        if (!currentUser || currentUser.role === "GUEST") {
            window.alert("Please log in again before removing a policy.");
            return;
        }

        const confirmed = window.confirm(
            `Remove policy rule "${ruleLabel}"? This action cannot be undone.`,
        );

        if (!confirmed) {
            return;
        }

        try {
            await deletePolicyRule(state.company.id, ruleId, {
                username: currentUser.email,
            });
            await refreshCompanyPolicies();
        } catch (error) {
            window.alert(getErrorMessage(error, "Failed to remove the policy rule."));
        }
    }

    async function handleRemoveDiscountRule(ruleId: string, ruleLabel: string) {
        if (!currentUser || currentUser.role === "GUEST") {
            window.alert("Please log in again before removing a discount.");
            return;
        }

        const confirmed = window.confirm(
            `Remove discount "${ruleLabel}"? This action cannot be undone.`,
        );

        if (!confirmed) {
            return;
        }

        try {
            await removeDiscount(state.company.id, ruleId, {
                username: currentUser.email,
            });
            await refreshCompanyPolicies();
        } catch (error) {
            window.alert(getErrorMessage(error, "Failed to remove the discount."));
        }
    }

    async function savePermissionDraft() {
        if (!currentUser || !selectedManagerNode) {
            return;
        }

        const managerEmail = extractEmailFromHierarchyLabel(selectedManagerNode.label);

        if (!managerEmail) {
            window.alert("This manager node does not contain an email address.");
            return;
        }

        const request: ChangeManagerPermissionsRequest = {
            ownerUsername: currentUser.email,
            managerUsername: managerEmail,
            newPermissions: permissionDraft,
        };

        try {
            await changeManagerPermissions(state.company.id, request);

            const hierarchyResponse = await getCompanyHierarchy(state.company.id, currentUser.email);
            const hierarchySource = hierarchyResponse.mermaidChart;
            const hierarchyRoots = parseMermaidHierarchy(hierarchyResponse.mermaidChart);
            const managerPermissionsByNodeId = parseManagerPermissionsFromMermaid(
                hierarchyResponse.mermaidChart,
            );

            let subordinateIds: string[] = [];

            if (hierarchyRoots.length > 0) {
                function findNode(
                    nodes: HierarchyNode[],
                    predicate: (node: HierarchyNode) => boolean,
                ): HierarchyNode | null {
                    for (const node of nodes) {
                        if (predicate(node)) {
                            return node;
                        }

                        const found = findNode(node.children, predicate);

                        if (found) {
                            return found;
                        }
                    }

                    return null;
                }

                const userNode = findNode(hierarchyRoots, (node) => {
                    return node.label.includes(currentUser.email);
                });

                if (userNode) {
                    const ids = new Set<string>();

                    function collectDescendants(node: HierarchyNode) {
                        for (const child of node.children) {
                            ids.add(child.id);
                            collectDescendants(child);
                        }
                    }

                    collectDescendants(userNode);
                    subordinateIds = [...ids];
                }
            }

            setState((currentState) => ({
                ...currentState,
                hierarchyRoots,
                hierarchySource,
                subordinateIds,
                managerPermissionsByNodeId,
            }));

            closePermissionsModal();
        } catch (error) {
            window.alert(getErrorMessage(error, "Failed to save manager permissions."));
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

            {isPoliciesVisible && (
                <CompanyPoliciesSection
                    purchaseRules={state.companyPolicies?.purchaseRules}
                    discountRules={state.companyPolicies?.discountRules}
                    onRemovePurchaseRule={handleRemovePurchasePolicyRule}
                    onRemoveDiscountRule={handleRemoveDiscountRule}
                />
            )}

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
                            Events directly managed by you in {company.name} are listed here.
                            You can manage these events or create new ones.
                        </p>
                    </div>

                    <button
                        type="button"
                        className="company-event-create-button"
                        onClick={() => {
                            onCreateEvent(state.company.id);
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
                            onOpenEvent={onSelectEvent}
                            onEditEvent={onEditEvent}
                            onStartRegularSale={handleStartRegularSale}
                            isStartingRegularSale={startingRegularSaleEventId === event.id}
                            onDelete={async (eventId, eventName) => {
                                const shouldDelete = window.confirm(
                                    `Delete event "${eventName}"? This cannot be undone.`,
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
                                const canManagePermissions = canRemoveMembers;

                                return state.hierarchyRoots.map((rootNode) => (
                                    <HierarchyBranch
                                        key={rootNode.id}
                                        node={rootNode}
                                        subordinateIds={subordinateSet}
                                        onRemove={handleRemoveMember}
                                        canRemove={canRemoveMembers}
                                        onManagePermissions={openPermissionsModal}
                                        canManagePermissions={canManagePermissions}
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

                    {isPermissionsModalOpen && selectedManagerNode && (
                        <div className="company-permissions-modal-backdrop" role="presentation">
                            <div
                                className="company-permissions-modal"
                                role="dialog"
                                aria-modal="true"
                                aria-labelledby="company-permissions-modal-title"
                            >
                                <div className="company-permissions-modal-header">
                                    <div>
                                        <h3 id="company-permissions-modal-title">Manage permissions</h3>
                                        <p>
                                            Adjust the permissions for {selectedManagerNode.label}.
                                        </p>
                                    </div>

                                    <button
                                        type="button"
                                        className="company-permissions-modal-close"
                                        onClick={closePermissionsModal}
                                        aria-label="Close permissions modal"
                                    >
                                        ×
                                    </button>
                                </div>

                                <div className="company-permissions-modal-current">
                                    <span className="company-permissions-modal-current-label">
                                        Current permissions
                                    </span>

                                    <div className="company-permissions-modal-chip-grid">
                                        {ALL_BACKEND_PERMISSIONS.map((permission) => {
                                            const isEnabled = permissionDraft.includes(permission);

                                            return (
                                                <button
                                                    key={permission}
                                                    type="button"
                                                    className={
                                                        isEnabled
                                                            ? "company-permissions-modal-chip company-permissions-modal-chip--active"
                                                            : "company-permissions-modal-chip"
                                                    }
                                                    onClick={() => togglePermissionDraft(permission)}
                                                >
                                                    {getPermissionLabel(permission)}
                                                </button>
                                            );
                                        })}
                                    </div>
                                </div>

                                <div className="company-permissions-modal-footer">
                                    <button
                                        type="button"
                                        className="company-permissions-modal-secondary"
                                        onClick={closePermissionsModal}
                                    >
                                        Cancel
                                    </button>

                                    <button
                                        type="button"
                                        className="company-permissions-modal-primary"
                                        onClick={savePermissionDraft}
                                    >
                                        Save
                                    </button>
                                </div>
                            </div>
                        </div>
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
    onManagePermissions,
    canManagePermissions,
}: {
    node: HierarchyNode;
    subordinateIds: Set<string>;
    onRemove: (emailToRemove: string) => Promise<void>;
    canRemove: boolean;
    onManagePermissions: (node: HierarchyNode) => void;
    canManagePermissions: boolean;
}) {
    const showRemove = canRemove && subordinateIds.has(node.id);
    const showManagePermissions =
        canManagePermissions &&
        subordinateIds.has(node.id) &&
        isManagerHierarchyLabel(node.label);

    return (
        <ul className="company-hierarchy-list">
            <li>
                <div className="company-hierarchy-node">
                    <span>{node.label}</span>

                    {showRemove && (
                        <button
                            type="button"
                            className="company-hierarchy-remove-button"
                            onClick={() => {
                                const email = extractEmailFromHierarchyLabel(node.label);

                                if (!email) {
                                    window.alert(
                                        "This node does not contain an email address yet. Please refresh after server restart so hierarchy labels are email-based.",
                                    );
                                    return;
                                }

                                onRemove(email);
                            }}
                        >
                            Remove from company
                        </button>
                    )}

                    {showManagePermissions && (
                        <button
                            type="button"
                            className="company-hierarchy-permissions-button"
                            onClick={() => onManagePermissions(node)}
                        >
                            Manage permissions
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
                                onManagePermissions={onManagePermissions}
                                canManagePermissions={canManagePermissions}
                            />
                        ))}
                    </div>
                )}
            </li>
        </ul>
    );
}

function extractEmailFromHierarchyLabel(label: string) {
    const emailMatch = label.match(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/);
    return emailMatch ? emailMatch[0] : null;
}