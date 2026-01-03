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
            // Default to localhost for development
            System.out.println("No CORS_ALLOWED_ORIGINS set, using localhost defaults");
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


