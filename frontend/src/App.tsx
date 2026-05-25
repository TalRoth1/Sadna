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
import CompanyCreationPage from "./pages/createCompany/CompanyCreationPage";
import CompanyPage from "./pages/companyPage/CompanyPage";
import UserProfilePage from "./pages/UserProfilePage";
import LoginPage from "./pages/LoginPage";
import RegistrationPage from "./pages/RegistrationPage";
import LotteryRegistrationPage from "./pages/LotteryRegistrationPage";
import MyActivePurchasesPage from "./pages/MyActivePurchasesPage";
import MyCompaniesPage from "./pages/myCompanies/MyCompaniesPage";
import QueueWaitingPage from "./pages/QueueWaitingPage";
import CreateEventPage from "./pages/createEvent/CreateEventPage";
import EditEventPage from "./pages/editEvent/EditEventPage";
import type { CompanyResponse } from "./services/companyService";
import type { AdminActionId } from "./types/admin";
import "./App.css";

type CompanyStatus = "Active" | "Suspended" | "Closed";

type CompanyPermissionName =
    | "Manage inventory"
    | "Configure layout"
    | "Manage policies"
    | "Customer service"
    | "View history"
    | "Generate sales reports";

type SelectedCompany = {
    id: string;
    name: string;
    role: string;
    status: CompanyStatus;
    permissions: CompanyPermissionName[];
};

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

function normalizeStatus(status: string): CompanyStatus {
    const normalizedStatus = status.trim().toLowerCase();

    if (normalizedStatus === "suspended") {
        return "Suspended";
    }

    if (normalizedStatus === "closed") {
        return "Closed";
    }

    return "Active";
}

function App() {
    const [currentPage, setCurrentPage] = useState<AppPage>("event-search");
    const [selectedEventId, setSelectedEventId] = useState<string | null>(null);
    const [selectionAccessExpiresAt, setSelectionAccessExpiresAt] = useState<string | null>(null);
    const [selectedCompany, setSelectedCompany] = useState<SelectedCompany | null>(null);
    const [createEventCompanyId, setCreateEventCompanyId] = useState<string | null>(null);

    function navigate(page: AppPage) {
        if (
            page !== "event-details" &&
            page !== "event-queue" &&
            page !== "event-purchase" &&
            page !== "edit-event"
        ) {
            setSelectedEventId(null);
            setSelectionAccessExpiresAt(null);
        }
        if (page !== "company-details") {
            setSelectedCompany(null);
        }
        setCurrentPage(page);
    }

    function handleCreateEventForCompany(companyId: string) {
        setCreateEventCompanyId(companyId);
        setCurrentPage("create-event");
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
        setSelectionAccessExpiresAt(null);
        setCurrentPage("event-queue");
    }

    function handleStartLotteryRegistration(eventId: string) {
        setSelectedEventId(eventId);
        setCurrentPage("lottery-registration");
    }

    function handleEditEvent(eventId: string) {
        setSelectedEventId(eventId);
        setCurrentPage("edit-event");
    }

    function handleStartCompanyCreation() {
        setCurrentPage("company-creation");
    }

    function handleOpenCompany(
        companyId: string,
        companyName: string,
        role: string,
        status: string,
        permissions: CompanyPermissionName[],
    ) {
        setSelectedCompany({
            id: companyId,
            name: companyName,
            role,
            status: normalizeStatus(status),
            permissions,
        });
        setCurrentPage("company-details");
    }

    function handleCompanyCreationSuccess(company: CompanyResponse) {
        setSelectedCompany({
            id: company.id,
            name: company.name,
            role: "Founder",
            status: company.isActive ? "Active" : "Closed",
            permissions: getPermissionsForRole("Founder"),
        });
        setCurrentPage("company-details");
    }

    function handleBackToEvent() {
        if (!selectedEventId) {
            setCurrentPage("event-search");
            return;
        }
        setSelectionAccessExpiresAt(null);
        setCurrentPage("event-details");
    }

    function handleSelectionAccessGranted(accessExpiresAt: string | null) {
        setSelectionAccessExpiresAt(accessExpiresAt);
        setCurrentPage("event-purchase");
    }

    function handleSelectionAccessExpired() {
        setSelectionAccessExpiresAt(null);
        setCurrentPage("event-queue");
    }

    function handleOpenActivePurchase(eventId: string) {
        setSelectedEventId(eventId);
        setSelectionAccessExpiresAt(null);
        setCurrentPage("event-purchase");
    }

    function handleEventCreated(eventId: string) {
        setSelectedEventId(eventId);
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
                    onEditEvent={handleEditEvent}
                />
            );
        }


        if (currentPage === "event-queue") {
            if (!selectedEventId) {
                return <EventSearchPage onSelectEvent={handleSelectEvent} />;
            }

            return (
                <QueueWaitingPage
                    eventId={selectedEventId}
                    onBackToEvent={handleBackToEvent}
                    onAccessGranted={handleSelectionAccessGranted}
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
                    selectionAccessExpiresAt={selectionAccessExpiresAt}
                    onSelectionAccessExpired={handleSelectionAccessExpired}
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

        if (currentPage === "edit-event") {
            if (!selectedEventId) {
                return <EventSearchPage onSelectEvent={handleSelectEvent} />;
            }

            return (
                <EditEventPage
                    eventId={selectedEventId}
                    onBackToEvent={handleBackToEvent}
                    onLogin={() => setCurrentPage("login")}
                    onRegister={() => setCurrentPage("registration")}
                    onEventUpdated={(eventId) => {
                        setSelectedEventId(eventId);
                        setCurrentPage("event-details");
                    }}
                />
            );
        }

        if (currentPage === "user-tickets") {
            return (
                <MyActivePurchasesPage
                    onOpenPurchase={handleOpenActivePurchase}
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
                <MyCompaniesPage
                    onCreateCompany={handleStartCompanyCreation}
                    onOpenCompany={handleOpenCompany}
                />
            );
        }

        if (currentPage === "company-creation") {
            return (
                <CompanyCreationPage onCreationSuccess={handleCompanyCreationSuccess} />
            );
        }

        if (currentPage === "company-details") {
            if (!selectedCompany) {
                return (
                    <MyCompaniesPage
                        onCreateCompany={handleStartCompanyCreation}
                        onOpenCompany={handleOpenCompany}
                    />
                );
            }

            return (
                <CompanyPage
                    company={selectedCompany}
                    onBackToCompanies={() => setCurrentPage("my-companies")}
                    onCreateEvent={handleCreateEventForCompany}
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

        if (currentPage === "create-event") {
            return (
                <CreateEventPage
                    initialCompanyId={createEventCompanyId}
                    onCreateCompany={handleStartCompanyCreation}
                    onLogin={() => setCurrentPage("login")}
                    onRegister={() => setCurrentPage("registration")}
                    onEventCreated={handleEventCreated}
                />
            );
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
