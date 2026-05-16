import { getCurrentUser } from "./currentUserService";
import { verifyPlatformAdmin } from "./admin/adminAuthService";
import type { UserProfile } from "../types/userProfile";

// TODO: Replace this mock implementation with a real communication call once the protocol/API is implemented.
// The server should return the profile details of the currently logged-in user.
// If no user is logged in, no profile should be returned.
export async function getCurrentUserProfile(): Promise<UserProfile | null> {
    const currentUser = await getCurrentUser();

    if (!currentUser) {
        return null;
    }

    const isAdmin = await verifyPlatformAdmin(currentUser.id);

    return {
        id: currentUser.id,
        username: currentUser.username,
        status: "active",
        role: isAdmin ? "admin" : "user",
    };
}