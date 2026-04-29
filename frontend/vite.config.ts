import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import { VitePWA } from "vite-plugin-pwa";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["favicon.ico", "apple-touch-icon.png", "mask-icon.svg"],
      manifest: {
        name: "FamilyApp",
        short_name: "FamilyApp",
        description: "Familjeapp för att hantera sysslor, schema och XP",
        theme_color: "#b8e6b8",
        background_color: "#faf8f5",
        display: "standalone",
        orientation: "portrait",
        scope: "/",
        start_url: "/",
        icons: [
          {
            src: "icon.svg",
            sizes: "any",
            type: "image/svg+xml"
          },
          {
            src: "icon.svg",
            sizes: "192x192",
            type: "image/svg+xml"
          },
          {
            src: "icon.svg",
            sizes: "512x512",
            type: "image/svg+xml"
          }
        ]
      },
      icon: "public/icon.svg",
      workbox: {
        skipWaiting: true, // New service worker activates immediately on deploy
        clientsClaim: true, // Take control of all open tabs immediately
        globPatterns: ["**/*.{js,css,html,ico,png,svg,woff2}"],
        globIgnores: ["**/pets/**/*.png"], // Exclude large pet images from precaching
        maximumFileSizeToCacheInBytes: 10 * 1024 * 1024,
        runtimeCaching: [
          {
            urlPattern: /^https:\/\/.*\.railway\.app\/api\/v1\/.*/i,
            handler: "NetworkFirst",
            options: {
              cacheName: "api-cache",
              expiration: {
                maxEntries: 50,
                maxAgeSeconds: 5 * 60 // 5 minutes
              },
              cacheableResponse: {
                statuses: [0, 200]
              }
            }
          },
          {
            // Cache pet images at runtime — StaleWhileRevalidate so new images
            // are always fetched in the background (fixes stale/missing images after deploy)
            urlPattern: /\/pets\/.*\.png$/i,
            handler: "StaleWhileRevalidate",
            options: {
              cacheName: "pet-images-cache-v3", // bumped to purge stale pet images after standalone-image redesign
              expiration: {
                maxEntries: 100,
                maxAgeSeconds: 7 * 24 * 60 * 60 // 7 days
              },
              cacheableResponse: {
                statuses: [0, 200]
              }
            }
          }
        ]
      }
    })
  ],
  server: {
    port: 5173
  },
  // Explicitly define environment variables for build
  define: {
    // Vite automatically replaces import.meta.env.VITE_API_BASE_URL at build time
    // This is just to ensure it's available
  }
});



