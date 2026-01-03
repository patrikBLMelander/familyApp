package com.familyapp.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Get allowed origins from environment variable
        String allowedOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        String[] origins;
        
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            allowedOrigins = allowedOrigins.replaceAll("^\"|\"$", "").trim();
            origins = allowedOrigins.split(",");
            for (int i = 0; i < origins.length; i++) {
                origins[i] = origins[i].trim();
            }
        } else {
            origins = new String[]{
                "https://familyapp-frontend-production.up.railway.app",
                "http://localhost:3000",
                "http://localhost:5173",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:5173"
            };
        }
        
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

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


