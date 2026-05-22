import type { UserResponse } from "../types/auth";

export type CurrentUser = {
    id: string;
    username: string;
    email: string;
    status: string;
    role: string;
    age: number;
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