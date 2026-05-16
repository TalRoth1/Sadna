import { useState } from "react";
import NavigationMenu from "./components/NavigationMenu";
import type { AppPage } from "./components/NavigationMenu";

import AdminAnalyticsPage from "./pages/admin/AdminAnalyticsPage";
import AdminCompaniesPage from "./pages/admin/AdminCompaniesPage";
import AdminComplaintsPage from "./pages/admin/AdminComplaintsPage";
import AdminDashboardPage from "./pages/admin/AdminDashboardPage";
import AdminPurchaseHistoryPage from "./pages/admin/AdminPurchaseHistoryPage";
import AdminQueuesPage from "./pages/admin/AdminQueuesPage";
import AdminSubscribersPage from "./pages/admin/AdminSubscribersPage";

import PurchaseHistoryPage from "./pages/PurchaseHistoryPage";
import EventSearchPage from "./pages/EventSearch/EventSearch";
import UserProfilePage from "./pages/UserProfilePage";
import LoginPage from "./pages/LoginPage";

import type { AdminActionId } from "./types/admin";
import "./App.css";

function App(){
    const [currentPage, setCurrentPage] = useState<AppPage>("event-search");

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
        if (currentPage === "event-search") {
            return <EventSearchPage />;
        }

        if (currentPage === "login") {
            return <LoginPage onLoginSuccess={() => setCurrentPage("event-search")} />;
        }

        if (currentPage === "purchase-history") {
            return <PurchaseHistoryPage />;
        }

        if (currentPage === "profile") {
            return <UserProfilePage />;
        }

        if (currentPage === "admin-dashboard") {
            return <AdminDashboardPage onNavigate={handleAdminNavigate} />;
        }

        if (currentPage === "admin-companies") return <AdminCompaniesPage />;
        if (currentPage === "admin-subscribers") return <AdminSubscribersPage />;
        if (currentPage === "admin-complaints") return <AdminComplaintsPage />;
        if (currentPage === "admin-purchases") return <AdminPurchaseHistoryPage />;
        if (currentPage === "admin-analytics") return <AdminAnalyticsPage />;
        if (currentPage === "admin-queues") return <AdminQueuesPage />;

        return <div className="app-page">Page not found</div>;
    }

    return (
        <>
            <NavigationMenu currentPage={currentPage} onNavigate={setCurrentPage} />
            {renderPage()}
        </>
    );
}

export default App;