import axios from 'axios';

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
    // timeout in milliseconds (10 seconds) if no response is received from the server, the request will be aborted
    timeout: 10000,
    headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
    },
});

// adding a token to the request header before sending the request to the server
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token');
        
        // הבטחה ל-TypeScript שה-headers קיימים ולא undefined
        if (token && config.headers) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// handling responses from the server, including successful responses and various error scenarios
api.interceptors.response.use(
    (response) => {
        return response;
    },
    (error) => {
        if (error.response) {
            const status = error.response.status;

            if (status === 401) {
                // Skip the auto-redirect when the failing request is the
                // user's authentication flow itself. These endpoints
                // legitimately return 401 as a user-facing outcome
                // ("incorrect email or password"), not as the
                // "token expired" signal the redirect is meant for.
                //   - /users/login    : bad credentials → caller shows error
                //   - /users/logout   : a 401 on logout would otherwise wipe
                //                       the token and loop back to /login
                //   - /users/register : duplicate email returns 401-ish
                //                       errors that the form should display
                //   - /users/guest    : guest bootstrap never has a token to
                //                       be expired in the first place
                const url = error.config?.url ?? "";
                const isAuthEndpoint =
                    url.includes("/users/login")
                    || url.includes("/users/logout")
                    || url.includes("/users/register")
                    || url.includes("/users/guest");

                if (!isAuthEndpoint) {
                    console.error("User not authenticated or token expired - redirecting to login page");
                    localStorage.removeItem('token');
                    // It is recommended to leave this on to react to the token expiration in real time
                    window.location.href = '/login';
                }
            }

            if (status === 403) {
                console.error("User does not have permission to perform this action (Forbidden)");
            }

            if (status >= 500) {
                console.error("Internal server error on the Java backend (Internal Server Error)\n" + error.response.data.message); // Assuming the backend sends a message in the response body for server errors
            }
        } else if (error.request) {
            console.error("Server is not responding. Make sure the Spring Boot project is running and the port is correct.");
        }

        return Promise.reject(error);
    }
);

export default api;