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
                console.error("User not authenticated or token expired - redirecting to login page");
                localStorage.removeItem('token');
                
                // מומלץ להשאיר את זה דולק כדי שהאפליקציה תגיב בזמן אמת לפקיעת טוקן
                window.location.href = '/login'; 
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