import { useState } from "react";
<<<<<<< HEAD
=======
import NavigationMenu from "./components/NavigationMenu";
import type { AppPage } from "./components/NavigationMenu";
>>>>>>> origin/navigation-menu
import AdminAnalyticsPage from "./pages/admin/AdminAnalyticsPage";
import AdminCompaniesPage from "./pages/admin/AdminCompaniesPage";
import AdminComplaintsPage from "./pages/admin/AdminComplaintsPage";
import AdminDashboardPage from "./pages/admin/AdminDashboardPage";
import AdminPurchaseHistoryPage from "./pages/admin/AdminPurchaseHistoryPage";
import AdminQueuesPage from "./pages/admin/AdminQueuesPage";
import AdminSubscribersPage from "./pages/admin/AdminSubscribersPage";
<<<<<<< HEAD
import type { AdminActionId } from "./types/admin";
import "./App.css";

type Page = "dashboard" | AdminActionId;

function App() {
    const [page, setPage] = useState<Page>("dashboard");

    function renderPage() {
        if (page === "companies") {
            return <AdminCompaniesPage />;
        }

        if (page === "subscribers") {
            return <AdminSubscribersPage />;
        }

        if (page === "complaints") {
            return <AdminComplaintsPage />;
        }

        if (page === "purchases") {
            return <AdminPurchaseHistoryPage />;
        }

        if (page === "analytics") {
            return <AdminAnalyticsPage />;
        }

        if (page === "queues") {
            return <AdminQueuesPage />;
        }

        return <AdminDashboardPage onNavigate={setPage} />;
    }

    return (
        <>
            {page !== "dashboard" && (
                <button className="back-button" type="button" onClick={() => setPage("dashboard")}>
                    Back to Admin Dashboard
                </button>
            )}

            {renderPage()}
        </>
    );
}

=======
import PurchaseHistoryPage from "./pages/PurchaseHistoryPage";
import type { AdminActionId } from "./types/admin";
import "./App.css";

function PlaceholderPage({
                             title,
                             description,
                         }: {
    title: string;
    description: string;
}) {
    return (
        <main className="app-page">
            <section className="page-header">
                <h1>{title}</h1>
                <p>{description}</p>
            </section>
        </main>
    );
}

function App() {
    const [currentPage, setCurrentPage] = useState<AppPage>("home");

    function handleAdminNavigate(page: AdminActionId) {
        const adminPageByAction: Record<AdminActionId, AppPage> = {
            companies: "admin-companies",
            subscribers: "admin-subscribers",
            complaints: "admin-complaints",
            purchases: "admin-purchases",
            analytics: "admin-analytics",
            queues: "admin-queues",
        };

        setCurrentPage(adminPageByAction[page]);
    }

    function renderPage() {
        if (currentPage === "home") {
            return (
                <PlaceholderPage
                    title="Home"
                    description="Main entry point for the event ticket purchasing system."
                />
            );
        }

        if (currentPage === "event-search") {
            return (
                <PlaceholderPage
                    title="Event Search"
                    description="Search and browse events."
                />
            );
        }

        if (currentPage === "user-tickets") {
            return (
                <PlaceholderPage
                    title="My Tickets"
                    description="Tickets owned by the current user."
                />
            );
        }

        if (currentPage === "purchase-history") {
            return <PurchaseHistoryPage />;
        }

        if (currentPage === "my-companies") {
            return (
                <PlaceholderPage
                    title="My Companies"
                    description="Companies that the current user belongs to."
                />
            );
        }

        if (currentPage === "profile") {
            return (
                <PlaceholderPage
                    title="Profile"
                    description="Current user profile details."
                />
            );
        }

        if (currentPage === "admin-dashboard") {
            return <AdminDashboardPage onNavigate={handleAdminNavigate} />;
        }

        if (currentPage === "admin-companies") {
            return <AdminCompaniesPage />;
        }

        if (currentPage === "admin-subscribers") {
            return <AdminSubscribersPage />;
        }

        if (currentPage === "admin-complaints") {
            return <AdminComplaintsPage />;
        }

        if (currentPage === "admin-purchases") {
            return <AdminPurchaseHistoryPage />;
        }

        if (currentPage === "admin-analytics") {
            return <AdminAnalyticsPage />;
        }

        if (currentPage === "admin-queues") {
            return <AdminQueuesPage />;
        }

        return (
            <PlaceholderPage
                title="Page not found"
                description="The requested page does not exist."
            />
        );
    }

    return (
        <>
            <NavigationMenu currentPage={currentPage} onNavigate={setCurrentPage} />
            {renderPage()}
        </>
    );
}

>>>>>>> origin/navigation-menu
export default App;