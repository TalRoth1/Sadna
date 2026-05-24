import { useEffect, useState } from "react";
import NotificationsPopup from "./NotificationsPopup";
import {
    getCurrentUser,
    type CurrentUser,
} from "../services/currentUserService";
import { verifyPlatformAdmin } from "../services/admin/adminAuthService";
import { logoutUser } from "../services/authService";

export type AppPage =
    | "event-search"
    | "event-details"
    | "event-purchase"
    | "login"
    | "registration"
    | "user-tickets"
    | "purchase-history"
    | "my-companies"
    | "company-creation"
    | "company-details"
    | "profile"
    | "admin-dashboard"
    | "admin-companies"
    | "admin-subscribers"
    | "admin-complaints"
    | "admin-purchases"
    | "admin-analytics"
    | "admin-queues"
    | "lottery-registration";

type NavigationMenuProps = {
    currentPage: AppPage;
    onNavigate: (page: AppPage) => void;
};

const mainLinks: { page: AppPage; label: string }[] = [
    { page: "event-search", label: "Event Search" },
    { page: "user-tickets", label: "My Active Purchases" },
    { page: "purchase-history", label: "Purchase History" },
    { page: "my-companies", label: "My Companies" },
    { page: "profile", label: "Profile" },
];

export default function NavigationMenu({
                                           currentPage,
                                           onNavigate,
                                       }: NavigationMenuProps) {
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
    const [isAdmin, setIsAdmin] = useState(false);
    const isLoggedIn = currentUser !== null;

    useEffect(() => {
        async function loadUserPermissions() {
            try {
                const user = await getCurrentUser();
                setCurrentUser(user);

                if (!user) {
                    setIsAdmin(false);
                    return;
                }

                const hasAdminAccess = await verifyPlatformAdmin(user.id);
                setIsAdmin(hasAdminAccess);
            } catch {
                setCurrentUser(null);
                setIsAdmin(false);
            }
        }

        loadUserPermissions();
    }, [currentPage]);

    function handleNavigate(page: AppPage) {
        onNavigate(page);
        setIsMenuOpen(false);
    }

    const visibleMainLinks = isLoggedIn
        ? mainLinks
        : mainLinks.filter((link) => link.page !== "my-companies");

    async function handleLogout() {
        try {
            console.log("Initiating logout process..."); // for debugging
            await logoutUser();
            console.log("Logout successful, clearing user state..."); // for debugging
        } finally {
            setCurrentUser(null);
            setIsAdmin(false);
            setIsMenuOpen(false);
            onNavigate("event-search");
        }
    }

    return (
        <>
            <header className="top-navigation">
                <button
                    type="button"
                    className="menu-toggle-button"
                    onClick={() => setIsMenuOpen(true)}
                    aria-label="Open navigation menu"
                >
                    <span />
                    <span />
                    <span />
                </button>

                <button
                    type="button"
                    className="navigation-brand"
                    onClick={() => handleNavigate("event-search")}
                >
                    Event Tickets
                </button>

                <div className="top-navigation-actions">
                    <NotificationsPopup currentUser={currentUser} />
                </div>
            </header>

            {isMenuOpen && (
                <button
                    type="button"
                    className="navigation-backdrop"
                    onClick={() => setIsMenuOpen(false)}
                    aria-label="Close navigation menu"
                />
            )}

            <aside className={`side-navigation ${isMenuOpen ? "open" : ""}`}>
                <div className="side-navigation-header">
                    <h2>Menu</h2>

                    <button
                        type="button"
                        className="close-menu-button"
                        onClick={() => setIsMenuOpen(false)}
                        aria-label="Close navigation menu"
                    >
                        ×
                    </button>
                </div>

                <nav className="side-navigation-links">
                    {visibleMainLinks.map((link) => (
                        <button
                            key={link.page}
                            type="button"
                            className={currentPage === link.page ? "active" : ""}
                            onClick={() => handleNavigate(link.page)}
                        >
                            {link.label}
                        </button>
                    ))}

                    {!isLoggedIn && (
                        <>
                            <div className="side-navigation-divider" />

                            <button
                                type="button"
                                className={currentPage === "login" ? "active" : ""}
                                onClick={() => handleNavigate("login")}
                            >
                                Login
                            </button>
                        </>
                    )}

                    {isAdmin && (
                        <>
                            <div className="side-navigation-divider" />

                            <button
                                type="button"
                                className={currentPage.startsWith("admin") ? "active" : ""}
                                onClick={() => handleNavigate("admin-dashboard")}
                            >
                                Admin Dashboard
                            </button>
                        </>
                    )}

                    {isLoggedIn && (
                        <>
                            <div className="side-navigation-divider" />

                            <button
                                type="button"
                                className="logout-menu-button"
                                onClick={handleLogout}
                            >
                                Logout
                            </button>
                        </>
                    )}
                </nav>
            </aside>
        </>
    );
}