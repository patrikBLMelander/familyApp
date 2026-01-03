package com.familyapp.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
        System.out.println("OPTIONS request received for: " + request.getRequestURI() + ", Origin: " + origin);
        
        // Get allowed origins from environment
        String allowedOrigins = System.getenv("CORS_ALLOWED_ORIGINS");
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            allowedOrigins = "https://familyapp-frontend-production.up.railway.app";
        } else {
            allowedOrigins = allowedOrigins.replaceAll("^\"|\"$", "").trim().split(",")[0].trim();
        }
        
        // Explicitly set CORS headers
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Origin", allowedOrigins);
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
        headers.add("Access-Control-Allow-Headers", "*");
        headers.add("Access-Control-Allow-Credentials", "true");
        headers.add("Access-Control-Max-Age", "3600");
        
        return ResponseEntity.ok().headers(headers).build();
    }
}

