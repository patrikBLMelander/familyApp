package com.familyapp.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global OPTIONS handler to ensure CORS preflight requests are handled
 * This explicitly adds CORS headers for OPTIONS requests
 */
@RestController
public class GlobalCorsController {

    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        String requestMethod = request.getHeader("Access-Control-Request-Method");
        String requestHeaders = request.getHeader("Access-Control-Request-Headers");
        System.out.println("OPTIONS request received for: " + request.getRequestURI());
        System.out.println("  Origin: " + origin);
        System.out.println("  Request-Method: " + requestMethod);
        System.out.println("  Request-Headers: " + requestHeaders);
        
        // Get allowed origins from environment or use defaults (same logic as CorsConfig)
        String allowedOriginsEnv = System.getenv("CORS_ALLOWED_ORIGINS");
        String allowedOrigin = null;
        
        if (allowedOriginsEnv != null && !allowedOriginsEnv.isEmpty()) {
            // Remove quotes if Railway added them
            allowedOriginsEnv = allowedOriginsEnv.replaceAll("^\"|\"$", "").trim();
            // Split by comma and check if origin matches any
            String[] originArray = allowedOriginsEnv.split(",");
            for (String envOrigin : originArray) {
                String trimmedOrigin = envOrigin.trim();
                if (origin != null && origin.equals(trimmedOrigin)) {
                    allowedOrigin = trimmedOrigin;
                    break;
                }
            }
            // If no match found, use first one
            if (allowedOrigin == null && originArray.length > 0) {
                allowedOrigin = originArray[0].trim();
            }
        } else {
            // Default origins (same as CorsConfig) - check if origin is in allowed list
            if (origin != null && (
                origin.equals("http://localhost:3000") ||
                origin.equals("http://localhost:5173") ||
                origin.equals("http://127.0.0.1:3000") ||
                origin.equals("http://127.0.0.1:5173") ||
                origin.equals("https://familyapp-frontend-production.up.railway.app")
            )) {
                allowedOrigin = origin;
            } else if (origin != null && origin.contains("railway.app")) {
                // Allow any Railway origin as fallback for production
                allowedOrigin = origin;
            } else {
                // Fallback: use the origin if provided, otherwise default to production frontend
                allowedOrigin = origin != null ? origin : "https://familyapp-frontend-production.up.railway.app";
            }
        }
        
        // Explicitly set CORS headers
        HttpHeaders headers = new HttpHeaders();
        if (allowedOrigin != null) {
            headers.add("Access-Control-Allow-Origin", allowedOrigin);
            System.out.println("  Setting Access-Control-Allow-Origin: " + allowedOrigin);
        }
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH, HEAD");
        headers.add("Access-Control-Allow-Headers", "*");
        headers.add("Access-Control-Allow-Credentials", "true");
        headers.add("Access-Control-Max-Age", "3600");
        
        System.out.println("  Returning OPTIONS response with CORS headers");
        return ResponseEntity.ok().headers(headers).build();
    }
}

