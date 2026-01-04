package com.familyapp.api.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Get allowed origins from environment variable or use defaults
        String allowedOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        System.out.println("CORS_ALLOWED_ORIGINS from env: " + allowedOrigins);
        
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            // Remove quotes if Railway added them
            allowedOrigins = allowedOrigins.replaceAll("^\"|\"$", "").trim();
            // Split by comma and trim each origin
            String[] originArray = allowedOrigins.split(",");
            for (int i = 0; i < originArray.length; i++) {
                originArray[i] = originArray[i].trim();
            }
            // Use setAllowedOrigins (not patterns) for exact matching - matches WorldCupPredictions
            config.setAllowedOrigins(Arrays.asList(originArray));
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
        
        // Allow all methods and common headers
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        // List common headers explicitly (wildcard doesn't work in Spring Boot 3.x)
        config.setAllowedHeaders(Arrays.asList(
            "Content-Type",
            "Authorization",
            "X-Device-Token",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        
        System.out.println("CORS config - Allowed methods: " + config.getAllowedMethods());
        System.out.println("CORS config - Allowed headers: " + config.getAllowedHeaders());
        System.out.println("CORS config - Allow credentials: " + config.getAllowCredentials());
        
        // Register for all paths
        source.registerCorsConfiguration("/**", config);
        
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE); // Ensure CORS filter runs first
        return bean;
    }
}


