import { useEffect, useState } from "react";
import { getCurrentUser } from "../../services/currentUserService";
import { getAdminActions } from "../../services/admin/adminDashboardService";
import type { AdminAction, AdminActionId } from "../../types/admin";

type AdminDashboardPageProps = {
    onNavigate: (page: AdminActionId) => void;
};

export default function AdminDashboardPage({ onNavigate }: AdminDashboardPageProps) {
    const [actions, setActions] = useState<AdminAction[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        async function loadDashboard() {
            try {
                setIsLoading(true);
                setErrorMessage("");

                const currentUser = await getCurrentUser();

                const isAdmin =
                    localStorage.getItem("userRole") === "ADMIN" ||
                    currentUser?.role === "ADMIN" ||
                    currentUser?.isAdmin === true;

                if (!currentUser || !isAdmin) {
                    setErrorMessage("You must be logged in as admin to access the admin dashboard.");
                    return;
                }

                const adminActions = await getAdminActions(currentUser.id);
                setActions(adminActions);

            } catch {
                setErrorMessage("You are not allowed to access the admin dashboard.");
            } finally {
                setIsLoading(false);
            }
        }

        loadDashboard();
    }, []);

    if (isLoading) {
        return (
            <main className="admin-page">
                <section className="empty-state">
                    <h2>Loading admin dashboard...</h2>
                    <p>Please wait while we verify your permissions.</p>
                </section>
            </main>
        );
    }

    if (errorMessage) {
        return (
            <main className="admin-page">
                <section className="empty-state">
                    <h2>Access denied</h2>
                    <p>{errorMessage}</p>
                </section>
            </main>
        );
    }

    return (
        <main className="admin-page">
            <section className="page-header">
                <h1>Admin Dashboard</h1>
                <p>Platform management actions available for system admins.</p>
            </section>

            <section className="admin-actions-grid">
                {actions.map((action) => (
                    <article key={action.id} className="admin-action-card">
                        <h2>{action.title}</h2>
                        <p>{action.description}</p>
                        <button type="button" onClick={() => onNavigate(action.id)}>
                            Open
                        </button>
                    </article>
                ))}
            </section>
        </main>
    );
}