export type LoginRequest = {
    username: string;
    password: string;
};

export type LoginResult = {
    userId: string;
    username: string;
};

export type RegistrationRequest = {
    username: string;
    password: string;
};

export type RegistrationResult = {
    userId: string;
    username: string;
};