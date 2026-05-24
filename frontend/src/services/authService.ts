import api from "./api";
import {
    clearCurrentUser,
    setCurrentUserFromResponse,
} from "./currentUserService";
import type {
    AuthResponse,
    LoginRequest,
    LoginResult,
    RegisterRequest,
    RegistrationResult,
} from "../types/auth";

function persistAuth(authResponse: AuthResponse): void {
    localStorage.setItem("token", authResponse.token);
    localStorage.setItem("userId", authResponse.user.userId);
    localStorage.setItem("userRole", authResponse.user.role);
    setCurrentUserFromResponse(authResponse.user);
}

export async function loginUser(request: LoginRequest): Promise<LoginResult> {
    const response = await api.post("/users/login", request);
    const authResponse = response.data.data as AuthResponse;

    persistAuth(authResponse);

    return authResponse;
}

// =============================================================================
// Guest session bootstrap
//
// Spec (general doc, §II.2.1): a visitor enters the platform as a guest and,
// on first ticket selection, an "active purchase" is opened for them.
// The backend models this with `POST /api/users/guest`, which creates a
// real User in the user repository, returns a signed JWT, and gives us a
// userId we can pass to PurchaseService.select{Sitting,Standing}Tickets.
//
// We keep the token under the same `token` localStorage key the shared
// axios interceptor in `api.ts` already attaches as Authorization: Bearer.
// The `userId` is cached separately so purchase calls can read it
// synchronously without re-decoding the JWT.
// =============================================================================

const TOKEN_KEY = "token";
const USER_ID_KEY = "userId";
const USER_ROLE_KEY = "userRole";

export type GuestSession = {
    userId: string;
    role: string;
};

type GuestEntryResponse = {
    isSuccess: boolean;
    message: string;
    token: string;
    userId: string;
    user: {
        userId: string;
        username: string;
        email: string;
        status: string;
        role: string;
        age: number;
    };
};

export function getStoredUserId(): string | null {
    return localStorage.getItem(USER_ID_KEY);
}

export function getStoredUserRole(): string | null {
    return localStorage.getItem(USER_ROLE_KEY);
}

export function isLoggedInMember(): boolean {
    return getStoredUserRole() === "MEMBER";
}

/**
 * Wipe the cached guest credentials. Call when the backend reports the
 * stored userId is no longer valid (typically because the in-memory user
 * repository was wiped on a server restart while the browser still held
 * the old localStorage entries). The next ensureGuestSession() will then
 * mint a fresh guest on the server.
 *
 * Members keep their localStorage entries intact — only guest sessions
 * are auto-cleared, because re-bootstrapping a logged-in member silently
 * would be wrong.
 */
export function clearGuestSession(): void {
    if (getStoredUserRole() !== "MEMBER") {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_ID_KEY);
        localStorage.removeItem(USER_ROLE_KEY);
    }
}

/**
 * Make sure the browser has a usable identity before calling any
 * purchase endpoint. If we already have a userId in localStorage we
 * reuse it. Otherwise we mint a fresh guest session on the server.
 *
 * Pass `forceFresh: true` to ignore the cache (used by the purchase
 * service's auto-recovery path when the server rejects the cached
 * session with "Your session is no longer valid").
 *
 * Returning the resolved userId lets callers chain straight into a
 * select{Sitting,Standing}Tickets call without re-reading storage.
 */
export async function ensureGuestSession(
    forceFresh: boolean = false,
): Promise<GuestSession> {
    if (!forceFresh) {
        const cachedId = getStoredUserId();
        const cachedRole = getStoredUserRole();
        if (cachedId && cachedRole) {
            return { userId: cachedId, role: cachedRole };
        }
    } else {
        clearGuestSession();
    }

    const response = await api.post("/users/guest");
    const data = response.data.data as GuestEntryResponse;

    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_ID_KEY, data.userId);
    localStorage.setItem(USER_ROLE_KEY, data.user.role);

    return { userId: data.userId, role: data.user.role };
}

/**
 * True when a backend error message indicates the cached session is
 * stale and should be rebootstrapped. Kept in one place so callers
 * stay in sync with whatever phrasing the backend uses.
 */
export function isSessionInvalidError(message: string): boolean {
    return message.toLowerCase().includes("session is no longer valid");
}

export async function registerUser(
    request: RegisterRequest,
): Promise<RegistrationResult> {
    const response = await api.post("/users/register", request);
    const authResponse = response.data.data as AuthResponse;

    persistAuth(authResponse);

    return authResponse;
}

export async function logoutUser(): Promise<void> {
    try {
        await api.post("/users/logout");
    }
    catch (error) {
        console.error("Logout failed:", error);
        // We proceed with local logout even if the server request fails, to ensure the user is logged out on the client side.
    } finally {
        localStorage.removeItem("token");
        localStorage.removeItem("userId");
        localStorage.removeItem("userRole");
        clearCurrentUser();
    }
}