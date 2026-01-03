// Runtime configuration for API base URL
// This allows us to set the API URL at runtime (via Railway environment variables)
// instead of requiring it at build-time

function getApiBaseUrl(): string {
  // First, try to read from window object (set by config.js or Railway env injection)
  if (typeof window !== 'undefined' && (window as any).APP_CONFIG?.API_BASE_URL) {
    return (window as any).APP_CONFIG.API_BASE_URL;
  }

  // Second, try to read from Vite env (build-time, works in dev)
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL;
  }

  // Third, try to infer from current location (for production)
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    // If we're on Railway frontend, use Railway backend
    if (hostname.includes('railway.app') || hostname.includes('railway')) {
      // Try to infer backend URL from frontend URL
      // This is a fallback - ideally we'd set it via environment variable
      return 'https://familyapp-backend-production.up.railway.app/api/v1';
    }
  }

  // Default fallback (for local development)
  return 'http://localhost:8080/api/v1';
}

export const API_BASE_URL = getApiBaseUrl();

// Debug: log the API URL being used (only in development)
if (import.meta.env.DEV) {
  console.log('API Base URL:', API_BASE_URL);
}

