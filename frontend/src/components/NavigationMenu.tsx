import { useEffect, useState } from "react";
import { getCurrentUser } from "../services/currentUserService";
import { verifyPlatformAdmin } from "../services/admin/adminAuthService";

export type AppPage =
    | "event-search"
    | "login"
    | "registration"
    | "user-tickets"
    | "purchase-history"
    | "my-companies"
    | "profile"
    | "admin-dashboard"
    | "admin-companies"
    | "admin-subscribers"
    | "admin-complaints"
    | "admin-purchases"
    | "admin-analytics"
    | "admin-queues";

type NavigationMenuProps = {
    currentPage: AppPage;
    onNavigate: (page: AppPage) => void;
};

const mainLinks: { page: AppPage; label: string }[] = [
    { page: "event-search", label: "Event Search" },
    { page: "user-tickets", label: "My Tickets" },
    { page: "purchase-history", label: "Purchase History" },
    { page: "my-companies", label: "My Companies" },
    { page: "profile", label: "Profile" },
];

export default function NavigationMenu({
                                           currentPage,
                                           onNavigate,
                                       }: NavigationMenuProps) {
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const [isAdmin, setIsAdmin] = useState(false);
    const [isLoggedIn, setIsLoggedIn] = useState(false);

    useEffect(() => {
        async function loadUserPermissions() {
            try {
                const currentUser = await getCurrentUser();

                if (!currentUser) {
                    setIsLoggedIn(false);
                    setIsAdmin(false);
                    return;
                }

                setIsLoggedIn(true);

                const hasAdminAccess = await verifyPlatformAdmin(currentUser.id);
                setIsAdmin(hasAdminAccess);
            } catch {
                setIsLoggedIn(false);
                setIsAdmin(false);
            }
        }

        loadUserPermissions();
    }, [currentPage]);

    function handleNavigate(page: AppPage) {
        onNavigate(page);
        setIsMenuOpen(false);
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
                    {mainLinks.map((link) => (
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
                </nav>
            </aside>
        </>
    );
}