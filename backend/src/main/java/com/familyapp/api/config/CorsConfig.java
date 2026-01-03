package com.familyapp.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        var config = new CorsConfiguration();
        
        // Get allowed origins from environment variable or use defaults
        // Match WorldCupPredictions approach
        String allowedOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        System.out.println("CORS_ALLOWED_ORIGINS from env: " + allowedOrigins);
        
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            // Remove quotes if Railway added them
            allowedOrigins = allowedOrigins.replaceAll("^\"|\"$", "").trim();
            // Split by comma and trim each origin
            config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
            System.out.println("Setting CORS allowed origins: " + config.getAllowedOrigins());
        } else {
            // Default: localhost for development + Railway frontend as fallback
            System.out.println("No CORS_ALLOWED_ORIGINS set, using defaults");
            config.setAllowedOrigins(Arrays.asList(
                    "https://familyapp-frontend-production.up.railway.app",
                    "http://localhost:3000",
                    "http://localhost:5173",
                    "http://127.0.0.1:3000",
                    "http://127.0.0.1:5173"
            ));
        }
        
        // Match WorldCupPredictions: allow all headers
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // Match WorldCupPredictions
        
        var source = new UrlBasedCorsConfigurationSource();
        // Register for all paths (match WorldCupPredictions)
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}


