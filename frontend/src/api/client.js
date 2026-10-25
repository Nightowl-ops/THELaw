import axios from 'axios';

// Base URL points to /api which Vite proxies to http://localhost:8085/api
// Or can connect directly to http://localhost:8085/api thanks to CORS in SecurityConfiguration.java
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Automatically attach JWT Bearer token to every outbound request
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('vv_token');
    if (token) {
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
    if (data.message) return data.message;
    if (data.error) return data.error;
    if (data.errors && typeof data.errors === 'object') {
      return Object.values(data.errors).join(', ');
    }
  }
  if (err.code === 'ERR_NETWORK') {
    return 'Cannot reach Spring Boot backend at http://localhost:8085. Make sure LawApplication is running (or switch to Interactive Demo Mode to preview the UI).';
  }
  return err.message || 'Request failed';
}

export default apiClient;
