import { setCurrentMockUser } from "./currentUserService";
import type {
    LoginRequest,
    LoginResult,
    RegistrationRequest,
    RegistrationResult,
} from "../types/auth";

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

// TODO: Replace this mock implementation with a real registration request once the communication layer is implemented.
// The server should validate the username, create the user, and return the created user details.
export async function registerUser(
    request: RegistrationRequest,
): Promise<RegistrationResult> {
    return new Promise((resolve, reject) => {
        setTimeout(() => {
            if (request.username.toLowerCase() === "taken") {
                reject(new Error("Username is already taken."));
                return;
            }

            const registeredUser = {
                userId: "registered-user-mock",
                username: request.username,
            };

            setCurrentMockUser({
                id: registeredUser.userId,
                username: registeredUser.username,
            });

            resolve(registeredUser);
        }, 500);
    });
}