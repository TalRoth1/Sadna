export type LoginRequest = {
    username: string;
    password: string;
};

export type LoginResult = {
    userId: string;
    username: string;
};