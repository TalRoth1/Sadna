import { useState } from "react";
import { loginUser } from "../services/authService";

type LoginPageProps = {
    onLoginSuccess: () => void;
    onNavigateToRegistration: () => void;
};

type LoginErrors = {
    email?: string;
    password?: string;
};

function validateLoginForm(email: string, password: string): LoginErrors {
    const errors: LoginErrors = {};

    if (!email.trim()) {
        errors.email = "Email is required.";
    }

    if (!password) {
        errors.password = "Password is required.";
    }

    return errors;
}

function getErrorMessage(error: unknown, fallback: string) {
    if (
        typeof error === "object" &&
        error !== null &&
        "response" in error &&
        typeof error.response === "object" &&
        error.response !== null &&
        "data" in error.response &&
        typeof error.response.data === "object" &&
        error.response.data !== null &&
        "message" in error.response.data &&
        typeof error.response.data.message === "string"
    ) {
        return error.response.data.message;
    }

    if (error instanceof Error) {
        return error.message;
    }

    return fallback;
}

export default function LoginPage({
                                      onLoginSuccess,
                                      onNavigateToRegistration,
                                  }: LoginPageProps) {
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");

    const [errors, setErrors] = useState<LoginErrors>({});
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();

        setErrorMessage("");

        const validationErrors = validateLoginForm(email, password);
        setErrors(validationErrors);

        if (Object.keys(validationErrors).length > 0) {
            return;
        }

        try {
            setIsSubmitting(true);

            await loginUser({
                email: email.trim(),
                plainPassword: password,
            });

            onLoginSuccess();
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Login failed."));
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <main className="app-page">
            <section className="page-header">
                <h1>Login</h1>
                <p>Sign in to your account.</p>
            </section>

            <form className="auth-card" onSubmit={handleSubmit}>
                <label className="form-field">
                    <span>Email</span>
                    <input
                        type="email"
                        value={email}
                        onChange={(event) => setEmail(event.target.value)}
                        placeholder="Enter email"
                    />
                    {errors.email && <small>{errors.email}</small>}
                </label>

                <label className="form-field">
                    <span>Password</span>
                    <input
                        type="password"
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                        placeholder="Enter password"
                    />
                    {errors.password && <small>{errors.password}</small>}
                </label>

                {errorMessage && <p className="form-error-message">{errorMessage}</p>}

                <button type="submit" disabled={isSubmitting}>
                    {isSubmitting ? "Logging in..." : "Login"}
                </button>

                <button
                    type="button"
                    className="secondary-auth-button"
                    onClick={onNavigateToRegistration}
                >
                    Create new account
                </button>
            </form>
        </main>
    );
}