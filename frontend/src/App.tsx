import { useState } from "react";
import AdminAnalyticsPage from "./pages/admin/AdminAnalyticsPage";
import AdminCompaniesPage from "./pages/admin/AdminCompaniesPage";
import AdminComplaintsPage from "./pages/admin/AdminComplaintsPage";
import AdminDashboardPage from "./pages/admin/AdminDashboardPage";
import AdminPurchaseHistoryPage from "./pages/admin/AdminPurchaseHistoryPage";
import AdminQueuesPage from "./pages/admin/AdminQueuesPage";
import AdminSubscribersPage from "./pages/admin/AdminSubscribersPage";
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

export default App;