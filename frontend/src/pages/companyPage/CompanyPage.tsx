import { useEffect, useState } from "react";
import {
    getCompanyHierarchy,
    getCompanyPermissions,
    type CompanyAccessResponse,
    type CompanyPermissionName as BackendCompanyPermissionName,
} from "../../services/companyService";
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
    hierarchyRoots: HierarchyNode[];
    hierarchyErrorMessage: string;
    hierarchySource: string;
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

function isHierarchyViewerRole(role: string) {
    const normalizedRole = role.trim().toLowerCase();
    return normalizedRole === "founder" || normalizedRole === "owner";
}

function normalizeMermaidLabel(value: string) {
    const withoutQuotes = value.trim().replace(/^"|"$/g, "");
    return withoutQuotes.replace(/\\n/g, " ").trim();
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

export default function CompanyPage({
    company,
    onBackToCompanies,
}: CompanyPageProps) {
    const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
    const [state, setState] = useState<CompanyPageState>({
        company,
        errorMessage: "",
        isLoading: true,
        hierarchyRoots: [],
        hierarchyErrorMessage: "",
        hierarchySource: "",
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

                const mappedCompany = mapAccessToViewModel(access, company);
                let hierarchyRoots: HierarchyNode[] = [];
                let hierarchyErrorMessage = "";
                let hierarchySource = "";

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

                setState({
                    company: mappedCompany,
                    errorMessage: "",
                    isLoading: false,
                    hierarchyRoots,
                    hierarchyErrorMessage,
                    hierarchySource,
                });
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

            {isHierarchyViewerRole(state.company.role) && (
                <section className="company-hierarchy-card">
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
                            {state.hierarchyRoots.map((rootNode) => (
                                <HierarchyBranch key={rootNode.id} node={rootNode} />
                            ))}
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
        </main>
    );
}

function HierarchyBranch({ node }: { node: HierarchyNode }) {
    return (
        <ul className="company-hierarchy-list">
            <li>
                <div className="company-hierarchy-node">{node.label}</div>
                {node.children.length > 0 && (
                    <div className="company-hierarchy-children">
                        {node.children.map((childNode) => (
                            <HierarchyBranch key={childNode.id} node={childNode} />
                        ))}
                    </div>
                )}
            </li>
        </ul>
    );
}