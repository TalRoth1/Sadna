import { getCurrentUser } from "./currentUserService";
import type { UserProfile } from "../types/userProfile";

export async function getCurrentUserProfile(): Promise<UserProfile | null> {
    const currentUser = await getCurrentUser();

    if (!currentUser) {
        return null;
    }

    return {
        id: currentUser.id,
        username: currentUser.username,
        email: currentUser.email,
        age: currentUser.age,
        status: currentUser.status,
        role: currentUser.role,
    };
}