import { useState } from "react";
import { loginUser } from "../services/authService";

type LoginPageProps = {
    onLoginSuccess: () => void;
};

type LoginErrors = {
    username?: string;
    password?: string;
};

function validateLoginForm(username: string, password: string): LoginErrors {
    const errors: LoginErrors = {};

    if (!username.trim()) {
        errors.username = "Username is required.";
    }

    if (!password) {
        errors.password = "Password is required.";
    }

    return errors;
}

export default function LoginPage({ onLoginSuccess }: LoginPageProps) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");

    const [errors, setErrors] = useState<LoginErrors>({});
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");
    const [infoMessage, setInfoMessage] = useState("");

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();

        setErrorMessage("");
        setInfoMessage("");

        const validationErrors = validateLoginForm(username, password);
        setErrors(validationErrors);

        if (Object.keys(validationErrors).length > 0) {
            return;
        }

        try {
            setIsSubmitting(true);

            await loginUser({
                username: username.trim(),
                password,
            });

            onLoginSuccess();
        } catch (error) {
            if (error instanceof Error) {
                setErrorMessage(error.message);
                return;
            }

            setErrorMessage("Login failed.");
        } finally {
            setIsSubmitting(false);
        }
    }

    function handleRegistrationClick() {
        setInfoMessage("Registration screen is not implemented yet.");
    }

    return (
        <main className="app-page">
            <section className="page-header">
                <h1>Login</h1>
                <p>Sign in to your account.</p>
            </section>

            <form className="auth-card" onSubmit={handleSubmit}>
                <label className="form-field">
                    <span>Username</span>
                    <input
                        type="text"
                        value={username}
                        onChange={(event) => setUsername(event.target.value)}
                        placeholder="Enter username"
                    />
                    {errors.username && <small>{errors.username}</small>}
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
                {infoMessage && <p className="form-info-message">{infoMessage}</p>}

                <button type="submit" disabled={isSubmitting}>
                    {isSubmitting ? "Logging in..." : "Login"}
                </button>

                <button
                    type="button"
                    className="secondary-auth-button"
                    onClick={handleRegistrationClick}
                >
                    Create new account
                </button>
            </form>
        </main>
    );
}