import api from "./api";
import {
    clearCurrentUser,
    setCurrentUserFromResponse,
} from "./currentUserService";
import type {
    AuthResponse,
    LoginRequest,
    LoginResult,
    RegisterRequest,
    RegistrationResult,
} from "../types/auth";

function persistAuth(authResponse: AuthResponse): void {
    localStorage.setItem("token", authResponse.token);
    setCurrentUserFromResponse(authResponse.user);
}

export async function loginUser(request: LoginRequest): Promise<LoginResult> {
    const response = await api.post("/users/login", request);
    const authResponse = response.data.data as AuthResponse;

    persistAuth(authResponse);

    return authResponse;
}

export async function registerUser(
    request: RegisterRequest,
): Promise<RegistrationResult> {
    const response = await api.post("/users/register", request);
    const authResponse = response.data.data as AuthResponse;

    persistAuth(authResponse);

    return authResponse;
}

export async function logoutUser(): Promise<void> {
    try {
        await api.post("/users/logout");
    } finally {
        localStorage.removeItem("token");
        clearCurrentUser();
    }
}