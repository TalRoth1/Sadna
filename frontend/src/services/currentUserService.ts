import type { UserResponse } from "../types/auth";
import api from "./api";

export type CurrentUser = {
    id: string;
    username: string;
    email: string;
    status: string;
    role: string;
    age: number;
    /**
     * True when the backend has this user registered as a system admin.
     * The User aggregate's `role` only distinguishes GUEST / MEMBER —
     * the admin facet is orthogonal and arrives on this flag.
     */
    isAdmin: boolean;
};

const CURRENT_USER_STORAGE_KEY = "currentUser";

function mapUserResponseToCurrentUser(user: UserResponse): CurrentUser {
    return {
        id: user.userId,
        username: user.username,
        email: user.email,
        status: user.status,
        role: user.role,
        age: user.age,
        // Default to false for older sessions that pre-date the field —
        // those callers will see the admin features stay hidden until
        // they log in again, which is the safer default.
        isAdmin: Boolean(user.isAdmin),
    };
}

export async function getCurrentUser(): Promise<CurrentUser | null> {
    const rawUser = localStorage.getItem(CURRENT_USER_STORAGE_KEY);

    if (!rawUser) {
        return null;
    }

    try {
        return JSON.parse(rawUser) as CurrentUser;
    } catch {
        localStorage.removeItem(CURRENT_USER_STORAGE_KEY);
        return null;
    }
}

export function setCurrentUserFromResponse(user: UserResponse): void {
    const currentUser = mapUserResponseToCurrentUser(user);
    localStorage.setItem(CURRENT_USER_STORAGE_KEY, JSON.stringify(currentUser));
}

export function clearCurrentUser(): void {
    localStorage.removeItem(CURRENT_USER_STORAGE_KEY);
}

/**
 * Validates the stored session against the server by calling GET /api/users/me.
 *
 * Why this is needed
 * ------------------
 * The backend uses in-memory storage. After a server restart all user data is
 * wiped, but the JWT in localStorage is still cryptographically valid (same
 * signing key). Without a server-side check the frontend would read the stale
 * localStorage and show the user as logged-in while every protected API call
 * silently fails.
 *
 * Behaviour
 * ---------
 * - No token in localStorage → clears any leftover user data → returns null.
 * - Token present, server confirms the user → refreshes localStorage with the
 *   latest user data from the server → returns the CurrentUser.
 * - Token present, but server rejects (401 = invalid/expired token or user
 *   gone after restart; network error = server down/restarted) → clears ALL
 *   auth keys from localStorage → returns null so the UI shows "logged out".
 */
export async function validateCurrentUserWithServer(): Promise<CurrentUser | null> {
    const token = localStorage.getItem("token");

    if (!token) {
        // No token at all — make sure no stale user data lingers.
        clearCurrentUser();
        return null;
    }

    try {
        const response = await api.get("/users/me");
        const user = response.data.data as UserResponse;

        // Refresh localStorage with the latest data from the server.
        setCurrentUserFromResponse(user);
        localStorage.setItem("userId", user.userId);
        localStorage.setItem("userRole", user.role);

        return await getCurrentUser();
    } catch {
        // Server rejected the session (stale after restart, expired token,
        // or unreachable server). Clear every auth key so the UI reflects
        // the true state.
        localStorage.removeItem("token");
        localStorage.removeItem("userId");
        localStorage.removeItem("userRole");
        clearCurrentUser();
        return null;
    }
}