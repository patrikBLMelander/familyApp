package com.familyapp.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global OPTIONS handler to ensure CORS preflight requests are handled
 * This is a fallback in case the CorsFilter doesn't catch all OPTIONS requests
 */
@RestController
public class GlobalCorsController {

    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        System.out.println("OPTIONS request received for: " + request.getRequestURI() + ", Origin: " + origin);
        
        // Let CorsFilter handle the headers, we just return 200
        return ResponseEntity.ok().build();
    }
}

