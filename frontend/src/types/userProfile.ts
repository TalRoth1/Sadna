export type UserProfile = {
    id: string;
    username: string;
    status: "active" | "blocked" | "removed";
    role: "user" | "admin";
};