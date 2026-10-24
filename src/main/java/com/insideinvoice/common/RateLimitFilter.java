package com.insideinvoice.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insideinvoice.auth.dto.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP fixed-window rate limiting on the unauthenticated, abuse-prone
 * surfaces: credential endpoints (login/signup/password-reset) and the public
 * share/contact API. Authenticated business endpoints stay un-throttled —
 * those requests are trusted and cheap, and a blanket cap would break real
 * multi-user bursts.
 *
 * <p>Deliberately dependency-free: an in-memory ConcurrentHashMap window per
 * (rule, client IP). A single app instance means no shared store is needed;
 * if the service ever scales horizontally, swap the map for Redis/bucket4j.
 * Internal errors in this filter fail OPEN — rate limiting must never be the
 * reason the API stops working; it only trims floods.</p>
 *
 * <p>Responses reuse the ApiResponse envelope with 429 and a Retry-After
 * header; every rejection logs a WARN carrying the MDC reqId (see
 * {@link RequestTraceFilter}) plus the offending key.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        // Spring's configured mapper (JavaTimeModule included) — a raw
        // `new ObjectMapper()` cannot serialise ApiResponse.timestamp
        // (LocalDateTime) and would turn every rejection into an exception,
        // which the error dispatch then mangles into a misleading 401.
        this.objectMapper = objectMapper;
    }

    private static final String REJECT_MESSAGE =
            "Too many requests from this address. Please slow down and try again shortly.";

    /** Per-limit rule: matching path prefix, window length and max hits. */
    private record Rule(String pathPrefix, int maxRequests, long windowMillis) {
        long windowSeconds() { return windowMillis / 1000; }
    }

    // ------------------------------------------------------------------ rules
    private static final List<Rule> RULES = List.of(
            // Credential brute forcing: a human retrying a foggy password fits
            // well inside 10/minute; a password sprayer does not.
            new Rule("/api/auth/login", 10, 60_000),
            // Account creation floods (also covers admin-created users, which
            // are one-per-form-submit, never a bulk loop).
            new Rule("/api/auth/signup", 5, 60_000),
            // Password-reset emails: each hit sends real mail — the strictest
            // cap of the three.
            new Rule("/api/auth/forgot-password", 3, 300_000),
            new Rule("/api/auth/reset-password", 5, 300_000),
            // Contact form SPAM/flooding.
            new Rule("/api/contact", 5, 300_000),
            // Public share pages are legit customer traffic (opening an
            // emailed invoice link, refreshing the page) — generous, but a
            // crawler hammering one IP still gets clipped.
            new Rule("/api/public", 60, 60_000)
    );

    /** key = rule fingerprint|client ip; value = {windowStartMs, hits}. */
    private static final ConcurrentHashMap<String, long[]> WINDOWS = new ConcurrentHashMap<>();

    // Bounded memory: prune once the table grows past this many keys.
    private static final int PRUNE_THRESHOLD = 4096;
    private static final long PRUNE_IDLE_MILLIS = 600_000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (pruneAndCheck(request, response)) {
            chain.doFilter(request, response);
        }
    }

    /** @return true when the request is allowed to proceed. */
    private boolean pruneAndCheck(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        for (Rule rule : RULES) {
            if (!path.startsWith(rule.pathPrefix())) {
                continue;
            }
            String key = keyOf(rule, request);
            long now = System.currentTimeMillis();
            long[] window = WINDOWS.compute(key, (k, w) -> (w == null || now - w[0] >= rule.windowMillis())
                    ? new long[]{now, 1}
                    : new long[]{w[0], w[1] + 1});
            if (window[1] <= rule.maxRequests()) {
                return true;
            }
            reject(response, rule, key);
            return false;
        }
        // No rule matched — authenticated/plain territory, not our business.
        if (WINDOWS.size() > PRUNE_THRESHOLD) {
            long cutoff = System.currentTimeMillis() - PRUNE_IDLE_MILLIS * 12;
            WINDOWS.values().removeIf(w -> w[0] < cutoff);
        }
        return true;
    }

    private String keyOf(Rule rule, HttpServletRequest request) {
        return rule.pathPrefix() + "|" + clientIp(request);
    }

    /**
     * Behind a reverse proxy the socket address is the proxy's own, so the
     * forwarded chain carries the real client first. Without a proxy the raw
     * remote address is used. (A direct-to-app caller cannot set us up with a
     * spoofed socket IP; a spoofed XFF line only self-rates its fake key.)
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response, Rule rule, String key) throws IOException {
        long[] window = WINDOWS.get(key);
        long retryAfter = window == null ? rule.windowMillis() / 1000
                : Math.max(1, (rule.windowMillis() - (System.currentTimeMillis() - window[0])) / 1000);
        log.warn("Rate limit exceeded at {} (max {} per {}s) — key {}", rule.pathPrefix(), rule.maxRequests(),
                rule.windowSeconds(), key);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(REJECT_MESSAGE)));
        } catch (Exception serializeFailure) {
            // Commit-proof fallback: status and Retry-After already stand; if
            // even the envelope fails to serialise, hand back the plain body
            // rather than letting an exception turn this into a 500/401.
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"" + REJECT_MESSAGE + "\"}");
        }
    }
}
