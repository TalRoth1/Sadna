import { useEffect, useState } from "react";
import { getCurrentUserProfile } from "../services/userProfileService";
import type { UserProfile } from "../types/userProfile";

function formatUserRole(role: UserProfile["role"]) {
    if (role === "admin") {
        return "System Admin";
    }

    return "Regular User";
}

function formatUserStatus(status: UserProfile["status"]) {
    if (status === "active") {
        return "Active";
    }

    if (status === "blocked") {
        return "Blocked";
    }

    return "Removed";
}

export default function UserProfilePage() {
    const [profile, setProfile] = useState<UserProfile | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState("");

    useEffect(() => {
        async function loadUserProfile() {
            try {
                setIsLoading(true);
                setErrorMessage("");

                const userProfile = await getCurrentUserProfile();
                setProfile(userProfile);
            } catch {
                setErrorMessage("Failed to load user profile.");
            } finally {
                setIsLoading(false);
            }
        }

        loadUserProfile();
    }, []);

    if (isLoading) {
        return (
            <main className="app-page">
                <section className="empty-state">
                    <h2>Loading profile...</h2>
                    <p>Please wait while we load your profile details.</p>
                </section>
            </main>
        );
    }

    if (errorMessage) {
        return (
            <main className="app-page">
                <section className="empty-state">
                    <h2>Something went wrong</h2>
                    <p>{errorMessage}</p>
                </section>
            </main>
        );
    }

    if (!profile) {
        return (
            <main className="app-page">
                <section className="empty-state">
                    <h2>No user is logged in</h2>
                    <p>Please log in to view your profile details.</p>
                </section>
            </main>
        );
    }

    return (
        <main className="app-page">
            <section className="page-header">
                <h1>User Profile</h1>
                <p>Basic details of the currently logged-in user.</p>
            </section>

            <section className="profile-card">
                <div className="profile-avatar">{profile.username.charAt(0).toUpperCase()}</div>

                <div className="profile-details">
                    <div className="profile-row">
                        <span>Username</span>
                        <strong>{profile.username}</strong>
                    </div>

                    <div className="profile-row">
                        <span>User ID</span>
                        <strong>{profile.id}</strong>
                    </div>

                    <div className="profile-row">
                        <span>Status</span>
                        <strong>{formatUserStatus(profile.status)}</strong>
                    </div>

                    <div className="profile-row">
                        <span>Role</span>
                        <strong>{formatUserRole(profile.role)}</strong>
                    </div>
                </div>
            </section>
        </main>
    );
}