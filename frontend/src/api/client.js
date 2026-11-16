import axios from 'axios';

// Base URL points to /api which Vite proxies to http://127.0.0.1:8080/api (API Gateway)
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Automatically attach JWT Bearer token to every outbound request (except public auth endpoints)
apiClient.interceptors.request.use(
  (config) => {
    const isPublicAuth = config.url?.startsWith('/auth/login') ||
                         config.url?.startsWith('/auth/register') ||
                         config.url?.startsWith('/auth/verify');
    const token = localStorage.getItem('vv_token');
    if (token && !isPublicAuth) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Standardized error message extractor matching GlobalExceptionHandler / ErrorResponse
export function extractErrorMessage(err) {
  if (!err) return 'An unexpected error occurred';
  if (err.response?.data) {
    const data = err.response.data;
    if (typeof data === 'string') return data;
    const validationMap = data.validationErrors || data.errors;
    if (validationMap && typeof validationMap === 'object') {
      return Object.values(validationMap).join(', ');
    }
    if (data.message) return data.message;
    if (data.error) return data.error;
  }
  if (err.code === 'ERR_NETWORK') {
    return 'Cannot reach API Gateway backend at http://localhost:8080. Make sure Docker containers are running.';
  }
  return err.message || 'Request failed';
}

export default apiClient;
