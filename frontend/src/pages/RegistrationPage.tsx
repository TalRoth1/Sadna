import { useState } from "react";
import { registerUser } from "../services/authService";

type RegistrationPageProps = {
    onRegistrationSuccess: () => void;
    onNavigateToLogin: () => void;
};

type RegistrationErrors = {
    username?: string;
    email?: string;
    age?: string;
    password?: string;
    confirmPassword?: string;
};

function validateRegistrationForm(
    username: string,
    email: string,
    age: string,
    password: string,
    confirmPassword: string,
): RegistrationErrors {
    const errors: RegistrationErrors = {};
    const parsedAge = Number(age);

    if (!username.trim()) {
        errors.username = "Username is required.";
    } else if (username.trim().length < 3) {
        errors.username = "Username must contain at least 3 characters.";
    }

    if (!email.trim()) {
        errors.email = "Email is required.";
    }

    if (!age.trim()) {
        errors.age = "Age is required.";
    } else if (!Number.isInteger(parsedAge) || parsedAge <= 0) {
        errors.age = "Age must be a positive number.";
    }

    if (!password) {
        errors.password = "Password is required.";
    } else if (password.length < 8) {
        errors.password = "Password must contain at least 8 characters.";
    }

    if (!confirmPassword) {
        errors.confirmPassword = "Password confirmation is required.";
    } else if (password !== confirmPassword) {
        errors.confirmPassword = "Passwords do not match.";
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

export default function RegistrationPage({
                                             onRegistrationSuccess,
                                             onNavigateToLogin,
                                         }: RegistrationPageProps) {
    const [username, setUsername] = useState("");
    const [email, setEmail] = useState("");
    const [age, setAge] = useState("");
    const [password, setPassword] = useState("");
    const [confirmPassword, setConfirmPassword] = useState("");

    const [errors, setErrors] = useState<RegistrationErrors>({});
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [errorMessage, setErrorMessage] = useState("");

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();

        setErrorMessage("");

        const validationErrors = validateRegistrationForm(
            username,
            email,
            age,
            password,
            confirmPassword,
        );

        setErrors(validationErrors);

        if (Object.keys(validationErrors).length > 0) {
            return;
        }

        try {
            setIsSubmitting(true);

            await registerUser({
                username: username.trim(),
                email: email.trim(),
                plainPassword: password,
                age: Number(age),
            });

            onRegistrationSuccess();
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Registration failed."));
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <main className="app-page">
            <section className="page-header">
                <h1>Registration</h1>
                <p>Create a new account.</p>
            </section>

            <form className="auth-card" onSubmit={handleSubmit}>
                <label className="form-field">
                    <span>Username</span>
                    <input
                        type="text"
                        value={username}
                        onChange={(event) => setUsername(event.target.value)}
                        placeholder="Choose username"
                    />
                    {errors.username && <small>{errors.username}</small>}
                </label>

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
                    <span>Age</span>
                    <input
                        type="number"
                        min="1"
                        value={age}
                        onChange={(event) => setAge(event.target.value)}
                        placeholder="Enter age"
                    />
                    {errors.age && <small>{errors.age}</small>}
                </label>

                <label className="form-field">
                    <span>Password</span>
                    <input
                        type="password"
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                        placeholder="Choose password"
                    />
                    {errors.password && <small>{errors.password}</small>}
                </label>

                <label className="form-field">
                    <span>Confirm Password</span>
                    <input
                        type="password"
                        value={confirmPassword}
                        onChange={(event) => setConfirmPassword(event.target.value)}
                        placeholder="Confirm password"
                    />
                    {errors.confirmPassword && <small>{errors.confirmPassword}</small>}
                </label>

                {errorMessage && <p className="form-error-message">{errorMessage}</p>}

                <button type="submit" disabled={isSubmitting}>
                    {isSubmitting ? "Registering..." : "Register"}
                </button>

                <button
                    type="button"
                    className="secondary-auth-button"
                    onClick={onNavigateToLogin}
                >
                    Already have an account? Login
                </button>
            </form>
        </main>
    );
}