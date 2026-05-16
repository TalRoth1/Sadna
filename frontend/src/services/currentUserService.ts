export type CurrentUser = {
    id: string;
    username: string;
};

// TODO: Replace this mock implementation with the real logged-in user/session data
// once authentication and the communication layer are implemented.
// Return null when there is no logged-in user.
export async function getCurrentUser(): Promise<CurrentUser | null> {
    return {
        id: "user-1",
        username: "mock-user",
    };

    // To test guest state:
    // return null;
}