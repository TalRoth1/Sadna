export type LoginRequest = {
    email: string;
    plainPassword: string;
};

export type RegisterRequest = {
    username: string;
    email: string;
    plainPassword: string;
    age: number;
};

export type UserResponse = {
    userId: string;
    username: string;
    email: string;
    status: string;
    role: string;
    age: number;
    isAdmin: boolean;
};

export type AuthResponse = {
    isSuccess: boolean;
    message: string;
    userId: string;
    token: string;
    user: UserResponse;
};

export type LoginResult = AuthResponse;

export type RegistrationResult = AuthResponse;