import axios from 'axios';

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
    // timeout in milliseconds (10 seconds) if no response is received from the server, the request will be aborted
    timeout: 30000,
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

                const url = error.config?.url ?? "";
                const isLogout = url.includes("/users/logout");

                if (!isLogout) {
                    console.error("User not authenticated or token expired - clearing auth data");
                    // Clear ALL auth-related keys so no stale data remains.
                    // This handles server restarts: the JWT is still valid
                    // cryptographically but the in-memory user is gone, so the
                    // backend returns 401. Clearing everything ensures the UI
                    // reflects the true "logged out" state on the next render.
                    localStorage.removeItem('token');
                    localStorage.removeItem('userId');
                    localStorage.removeItem('userRole');
                    localStorage.removeItem('currentUser');
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