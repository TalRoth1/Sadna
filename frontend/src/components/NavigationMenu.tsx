import { useEffect, useState } from "react";
import NotificationsPopup from "./NotificationsPopup";
import {
    validateCurrentUserWithServer,
    type CurrentUser,
} from "../services/currentUserService";
import { verifyPlatformAdmin } from "../services/admin/adminAuthService";
import { logoutUser } from "../services/authService";

export type AppPage =
    | "event-search"
    | "event-details"
    | "event-queue"
    | "event-purchase"
    | "login"
    | "registration"
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
    | "lottery-registration"
    | "create-event"
    | "edit-event";

type NavigationMenuProps = {
    currentPage: AppPage;
    onNavigate: (page: AppPage) => void;
};

const mainLinks: { page: AppPage; label: string }[] = [
    { page: "event-search", label: "Event Search" },
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

    const isGuest =
        currentUser?.role === "GUEST" ||
        currentUser?.username?.startsWith("guest-");

    const isLoggedIn =
        currentUser !== null &&
        !isGuest &&
        currentUser.status === "LOGGED_IN";

    useEffect(() => {
        let isMounted = true;

        async function loadUserPermissions() {
            try {
                const user = await validateCurrentUserWithServer();

                if (!isMounted) {
                    return;
                }

                console.log("[NavigationMenu] current user:", user);

                setCurrentUser(user);

                if (!user) {
                    console.log("[NavigationMenu] no user, hiding admin dashboard");
                    setIsAdmin(false);
                    return;
                }

                const isGuestUser =
                    user.role === "GUEST" ||
                    user.username?.startsWith("guest-");

                if (isGuestUser) {
                    console.log("[NavigationMenu] guest user, skipping admin check");
                    setIsAdmin(false);
                    return;
                }

                if (!user.isAdmin) {
                    console.log("[NavigationMenu] non-admin user, skipping admin check");
                    setIsAdmin(false);
                    return;
                }

                const hasAdminAccess = await verifyPlatformAdmin(user.id);

                if (!isMounted) {
                    return;
                }

                console.log("[NavigationMenu] verifyPlatformAdmin userId:", user.id);
                console.log("[NavigationMenu] hasAdminAccess:", hasAdminAccess);

                setIsAdmin(hasAdminAccess);
            } catch (error) {
                if (!isMounted) {
                    return;
                }

                console.error("[NavigationMenu] failed to load user permissions:", error);

                setCurrentUser(null);
                setIsAdmin(false);
            }
        }

        void loadUserPermissions();

        function handleInvalidSession() {
            setCurrentUser(null);
            setIsAdmin(false);
            setIsMenuOpen(false);
        }

        window.addEventListener("auth-session-invalid", handleInvalidSession);

        return () => {
            isMounted = false;
            window.removeEventListener("auth-session-invalid", handleInvalidSession);
        };
    }, [currentPage]);

    function handleNavigate(page: AppPage) {
        onNavigate(page);
        setIsMenuOpen(false);
    }

    const visibleMainLinks = isLoggedIn
        ? mainLinks
        : mainLinks.filter((link) => link.page === "event-search");

    async function handleLogout() {
        try {
            console.log("Initiating logout process...");
            await logoutUser();
            console.log("Logout successful, clearing user state...");
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
                    <NotificationsPopup currentUser={isLoggedIn ? currentUser : null} />
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