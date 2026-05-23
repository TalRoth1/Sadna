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
import EventDetailsPage from "./pages/EventDetails/EventDetails";
import EventSearchPage from "./pages/EventSearch/EventSearch";
import PurchaseHistoryPage from "./pages/PurchaseHistoryPage";
import TicketPurchasePage from "./pages/TicketPurchase/TicketPurchase";
import UserProfilePage from "./pages/UserProfilePage";
import LoginPage from "./pages/LoginPage";
import RegistrationPage from "./pages/RegistrationPage";
import LotteryRegistrationPage from "./pages/LotteryRegistrationPage";

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
    const [currentPage, setCurrentPage] = useState<AppPage>("event-search");
    const [selectedEventId, setSelectedEventId] = useState<string | null>(null);

    function navigate(page: AppPage) {
        if (page !== "event-details" && page !== "event-purchase") {
            setSelectedEventId(null);
        }
        setCurrentPage(page);
    }

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

    function handleSelectEvent(eventId: string) {
        setSelectedEventId(eventId);
        setCurrentPage("event-details");
    }

    function handleBackToSearch() {
        setSelectedEventId(null);
        setCurrentPage("event-search");
    }

    function handleStartPurchase(eventId: string) {
        setSelectedEventId(eventId);
        setCurrentPage("event-purchase");
    }

    function handleStartLotteryRegistration(eventId: string) {
        setSelectedEventId(eventId);
        setCurrentPage("lottery-registration");
    }

    function handleBackToEvent() {
        if (!selectedEventId) {
            setCurrentPage("event-search");
            return;
        }
        setCurrentPage("event-details");
    }

    function renderPage() {
        if (currentPage === "event-search") {
            return <EventSearchPage onSelectEvent={handleSelectEvent} />;
        }

        if (currentPage === "event-details") {
            if (!selectedEventId) {
                return <EventSearchPage onSelectEvent={handleSelectEvent} />;
            }
            return (
                    <EventDetailsPage
                        eventId={selectedEventId}
                        onBackToSearch={handleBackToSearch}
                        onStartPurchase={handleStartPurchase}
                        onStartLotteryRegistration={handleStartLotteryRegistration}
                    />
            );
        }

        if (currentPage === "event-purchase") {
            if (!selectedEventId) {
                return <EventSearchPage onSelectEvent={handleSelectEvent} />;
            }
            return (
                // `key` forces React to remount TicketPurchasePage when the
                // user switches events. That way each event page gets its
                // own state (selection, resumed reservation, timer) and
                // they don't bleed into each other — which is critical for
                // the "parallel independence" requirement, since the
                // backend can now hold concurrent reservations across
                // different events for the same user.
                <TicketPurchasePage
                    key={selectedEventId}
                    eventId={selectedEventId}
                    onBackToEvent={handleBackToEvent}
                />
            );
        }

        if (currentPage === "lottery-registration") {
            if (!selectedEventId) {
                return <EventSearchPage onSelectEvent={handleSelectEvent} />;
            }

            return (
                <LotteryRegistrationPage
                    eventId={selectedEventId}
                    onBackToEvent={handleBackToEvent}
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

        if (currentPage === "login") {
            return (
                <LoginPage
                    onLoginSuccess={() => setCurrentPage("event-search")}
                    onNavigateToRegistration={() => setCurrentPage("registration")}
                />
            );
        }

        if (currentPage === "registration") {
            return (
                <RegistrationPage
                    onRegistrationSuccess={() => setCurrentPage("event-search")}
                    onNavigateToLogin={() => setCurrentPage("login")}
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
            return <UserProfilePage />;
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
            <NavigationMenu currentPage={currentPage} onNavigate={navigate} />
            {renderPage()}
        </>
    );
}

export default App;
