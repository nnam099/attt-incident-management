import axios from 'axios';

let inMemoryToken: string | null = null;
let inMemoryRefreshToken: string | null = null;

export const setTokens = (token: string, refreshToken: string) => {
    inMemoryToken = token;
    inMemoryRefreshToken = refreshToken;
};

export const clearTokens = () => {
    inMemoryToken = null;
    inMemoryRefreshToken = null;
};

export const getAccessToken = () => inMemoryToken;
export const getRefreshToken = () => inMemoryRefreshToken;

const api = axios.create({
    baseURL: 'http://localhost:8080/api', // Chỉnh sửa theo port thực tế của Backend
    headers: {
        'Content-Type': 'application/json',
    },
});

api.interceptors.request.use((config) => {
    if (inMemoryToken && config.headers) {
        config.headers.Authorization = `Bearer ${inMemoryToken}`;
    }
    return config;
}, (error) => {
    return Promise.reject(error);
});

// Interceptor xử lý Refresh Token khi gặp lỗi 401
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;
        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true;
            if (inMemoryRefreshToken) {
                try {
                    const res = await axios.post('http://localhost:8080/api/auth/refresh', {
                        refreshToken: inMemoryRefreshToken,
                    });
                    setTokens(res.data.token, res.data.refreshToken);
                    originalRequest.headers.Authorization = `Bearer ${res.data.token}`;
                    return api(originalRequest);
                } catch (err) {
                    clearTokens();
                    window.location.href = '/login';
                    return Promise.reject(err);
                }
            } else {
                clearTokens();
                window.location.href = '/login';
            }
        }
        return Promise.reject(error);
    }
);

export default api;
