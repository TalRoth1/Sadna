export type CurrentUser = {
    id: string;
    username: string;
};

let currentMockUser: CurrentUser | null = null;

// TODO: Replace this mock implementation with real logged-in user/session data
// once authentication and the communication layer are implemented.
// Return null when there is no logged-in user.
export async function getCurrentUser(): Promise<CurrentUser | null> {
    return currentMockUser;
}

// TODO: Replace this mock setter with real session/token handling once communication is implemented.
export function setCurrentMockUser(user: CurrentUser): void {
    currentMockUser = user;
}

// TODO: Replace this mock logout with real session/token clearing once communication is implemented.
export function clearCurrentMockUser(): void {
    currentMockUser = null;
}