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
        if (railwayOrigins != null && !railwayOrigins.isEmpty()) {
            // Use Railway origins from environment variable
            config.setAllowedOriginPatterns(List.of(railwayOrigins.split(",")));
        } else {
            // Default to localhost for development
            config.setAllowedOriginPatterns(List.of(
                    "http://localhost:3000",
                    "http://localhost:5173",
                    "http://127.0.0.1:3000",
                    "http://127.0.0.1:5173"
            ));
        }
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Origin", "Content-Type", "Accept", "Authorization", "X-Device-Token"));
        config.setAllowCredentials(false);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}


