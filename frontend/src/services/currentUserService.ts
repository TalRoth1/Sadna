import type { UserResponse } from "../types/auth";

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