import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173
  },
  // Explicitly define environment variables for build
  define: {
    // Vite automatically replaces import.meta.env.VITE_API_BASE_URL at build time
    // This is just to ensure it's available
  }
});



