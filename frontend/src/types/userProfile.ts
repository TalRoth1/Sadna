export type UserProfile = {
    id: string;
    username: string;
    email: string;
    age: number;
    status: string;
    role: string;
    /**
     * Mirrors {@link CurrentUser.isAdmin}. The user's `role` only
     * conveys MEMBER / GUEST; this flag lets the profile UI render
     * "System Admin" for users who hold the orthogonal admin capability.
     */
    isAdmin: boolean;
};