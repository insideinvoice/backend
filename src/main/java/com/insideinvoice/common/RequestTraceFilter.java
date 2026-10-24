package com.insideinvoice.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * Puts a short random id of every HTTP request into the SLF4J MDC as
 * {@code reqId}, via logging.pattern's {@code %X{reqId}}. When anything goes
 * wrong, grepping for one request's id in the log pulls its whole story — the
 * controller branch, the warn/error from the advice, the stack — even when
 * many requests interleave under load. The key is removed in finally so a
 * recycled worker thread can never leak one request's id into the next.
 */
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    // No hex 0/O/1/L ambiguity beyond 8 chars — short enough to eyeball,
    // random enough that a million requests a day stay collision-free.
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String previous = MDC.get("reqId");
        MDC.put("reqId", String.format("%08x", RANDOM.nextInt()));
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.remove("reqId");
            } else {
                MDC.put("reqId", previous);
            }
        }
    }
}
