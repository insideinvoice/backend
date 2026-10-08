package com.insideinvoice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insideinvoice.auth.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes 403 bodies in the ApiResponse envelope. Without this, Spring Security's
 * default handler called sendError(403) and Boot's whitelabel /error produced a
 * second, non-envelope error shape.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
        try {
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error("Access denied")));
        } catch (Exception e) {
            response.getWriter().write("{\"success\":false,\"message\":\"Access denied\"}");
        }
    }
}
