package com.familyapp.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        var config = new CorsConfiguration();
        
        // Allow Railway domains (will be set via environment variable in production)
        String railwayOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        System.out.println("CORS_ALLOWED_ORIGINS from env: " + railwayOrigins);
        
        if (railwayOrigins != null && !railwayOrigins.isEmpty()) {
            // Remove quotes if Railway added them and split by comma
            railwayOrigins = railwayOrigins.replaceAll("^\"|\"$", "").trim();
            String[] origins = railwayOrigins.split(",");
            for (int i = 0; i < origins.length; i++) {
                origins[i] = origins[i].trim();
            }
            System.out.println("Setting CORS allowed origins: " + java.util.Arrays.toString(origins));
            config.setAllowedOriginPatterns(List.of(origins));
        } else {
            // Default: Allow Railway frontend domain if no env var is set
            // This is a fallback for production
            System.out.println("No CORS_ALLOWED_ORIGINS set, using Railway frontend as fallback");
            config.setAllowedOriginPatterns(List.of(
                    "https://familyapp-frontend-production.up.railway.app",
                    "http://localhost:3000",
                    "http://localhost:5173",
                    "http://127.0.0.1:3000",
                    "http://127.0.0.1:5173"
            ));
        }
        
        // Allow all necessary methods including OPTIONS for preflight
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("Origin", "Content-Type", "Accept", "Authorization", "X-Device-Token"));
        config.setExposedHeaders(List.of("Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L); // Cache preflight for 1 hour

        var source = new UrlBasedCorsConfigurationSource();
        // Register CORS for all API endpoints
        source.registerCorsConfiguration("/api/**", config);
        // Also register for root to catch any edge cases
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}


