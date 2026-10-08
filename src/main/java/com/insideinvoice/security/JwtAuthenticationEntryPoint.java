package com.insideinvoice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insideinvoice.auth.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes the 401 body for unauthenticated requests in the same ApiResponse envelope
 * the rest of the API uses (the previous hand-built JSON string bypassed Jackson,
 * declared no charset and omitted the WWW-Authenticate challenge).
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
        try {
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error("Unauthorized. Please provide a valid JWT token.")));
        } catch (Exception e) {
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"Unauthorized. Please provide a valid JWT token.\"}");
        }
    }
}
