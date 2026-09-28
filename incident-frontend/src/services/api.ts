import axios from 'axios';

let inMemoryToken: string | null = null;

export interface AuthPayload {
    token: string;
    username: string;
    roles: string[];
}

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api').replace(/\/$/, '');
export const BACKEND_BASE_URL = API_BASE_URL.replace(/\/api$/, '');

export const setAccessToken = (token: string) => { inMemoryToken = token; };
export const clearTokens = () => { inMemoryToken = null; };
export const getAccessToken = () => inMemoryToken;

const api = axios.create({
    baseURL: API_BASE_URL,
    withCredentials: true,
    headers: { 'Content-Type': 'application/json' },
});

export const refreshSession = async (): Promise<AuthPayload> => {
    const response = await axios.post<AuthPayload>(`${API_BASE_URL}/auth/refresh`, {}, { withCredentials: true });
    setAccessToken(response.data.token);
    return response.data;
};

export const revokeSession = async () => {
    await axios.post(`${API_BASE_URL}/auth/logout`, {}, { withCredentials: true });
};

api.interceptors.request.use(config => {
    if (inMemoryToken && config.headers) config.headers.Authorization = `Bearer ${inMemoryToken}`;
    return config;
});

let refreshInFlight: Promise<AuthPayload> | null = null;

api.interceptors.response.use(
    response => response,
    async error => {
        const originalRequest = error.config;
        const isAuthEndpoint = typeof originalRequest?.url === 'string' && originalRequest.url.includes('/auth/');
        if (error.response?.status === 401 && originalRequest && !originalRequest._retry && !isAuthEndpoint) {
            originalRequest._retry = true;
            try {
                refreshInFlight ??= refreshSession().finally(() => { refreshInFlight = null; });
                const auth = await refreshInFlight;
                originalRequest.headers = originalRequest.headers ?? {};
                originalRequest.headers.Authorization = `Bearer ${auth.token}`;
                return api(originalRequest);
            } catch (refreshError) {
                clearTokens();
                window.dispatchEvent(new Event('auth-expired'));
                return Promise.reject(refreshError);
            }
        }
        return Promise.reject(error);
    }
);

export default api;
