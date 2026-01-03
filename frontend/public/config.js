// Runtime configuration for API base URL
// This file is loaded at runtime, so Railway environment variables work correctly
window.APP_CONFIG = {
  API_BASE_URL: window.APP_CONFIG?.API_BASE_URL || 'http://localhost:8080/api/v1'
};

