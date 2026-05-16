import { setCurrentMockUser } from "./currentUserService";
import type { LoginRequest, LoginResult } from "../types/auth";

// TODO: Replace this mock implementation with a real login request once the communication layer is implemented.
// The server should validate the username/password and return the logged-in user/session data.
export async function loginUser(request: LoginRequest): Promise<LoginResult> {
    return new Promise((resolve, reject) => {
        setTimeout(() => {
            if (request.username === "mock-user" && request.password === "123456") {
                const loggedInUser = {
                    userId: "user-1",
                    username: "mock-user",
                };

                setCurrentMockUser({
                    id: loggedInUser.userId,
                    username: loggedInUser.username,
                });

                resolve(loggedInUser);
                return;
            }

            reject(new Error("Invalid username or password."));
        }, 500);
    });
}