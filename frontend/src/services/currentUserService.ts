export type CurrentUser = {
    id: string;
    username: string;
};

// TODO: Replace mock current user with real authenticated user data once authentication/session handling is implemented.
export async function getCurrentUser(): Promise<CurrentUser> {
    return {
        id: "user-1",
        username: "mock-user",
    };
}