package com.insideinvoice.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insideinvoice.auth.dto.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Guard for anonymous invoice endpoints: privacy/cache headers on every response
 * (success and error alike) plus a process-local sliding-window rate limit.
 *
 * <p>Honest scope: counters live in this JVM's memory. Railway runs a single instance
 * today, so one process sees all traffic - but this is NOT distributed protection. If
 * the service is ever scaled horizontally, replace the limiter with a shared store; the
 * configuration knobs ({@code app.public.rate-limit.*}) are already externalised.</p>
 *
 * <p>Client IP: the remote socket address, unless {@code app.security.trust-forwarded-for}
 * is enabled, in which case the LAST {@code X-Forwarded-For} entry is used - correct only
 * when a single trusted proxy (Railway's ingress) appends to the header and the app port
 * is not reachable directly. The header is never trusted to make authorization decisions;
 * it only shapes best-effort throttling, and key-table sizes are capped regardless.</p>
 */
@Component
@RequiredArgsConstructor
public class PublicEndpointGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PublicEndpointGuardFilter.class);
    private static final String PUBLIC_PREFIX = "/api/public/";
    private static final String OVERFLOW_KEY = "overflow:shared";
    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_IP_KEYS = 10_000;
    private static final int MAX_TOKEN_KEYS = 20_000;

    private final ObjectMapper objectMapper;

    @Value("${app.public.rate-limit.requests-per-minute-per-ip:120}")
    private int ipPerMinute;

    @Value("${app.public.rate-limit.requests-per-minute-per-token:60}")
    private int tokenPerMinute;

    @Value("${app.security.trust-forwarded-for:true}")
    private boolean trustForwardedFor;

    private final Map<String, Window> ipWindows = new ConcurrentHashMap<>();
    private final Map<String, Window> tokenWindows = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep = new AtomicLong(System.currentTimeMillis());

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith(PUBLIC_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        // Applied before anything else so rate-limited (429), not-found (404) and success
        // responses all carry the same privacy headers.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");

        String clientIp = clientIp(request);
        if (!allow(ipWindows, "ip:" + clientIp, ipPerMinute, MAX_IP_KEYS)) {
            reject(response, "Too many requests. Please try again later.");
            return;
        }

        String token = pathToken(path);
        if (token != null && !allow(tokenWindows, "tk:" + token, tokenPerMinute, MAX_TOKEN_KEYS)) {
            reject(response, "Too many requests. Please try again later.");
            return;
        }

        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body;
        try {
            body = objectMapper.writeValueAsString(ApiResponse.error(message));
        } catch (JsonProcessingException e) {
            body = "{\"success\":false,\"message\":\"Too many requests. Please try again later.\"}";
        }
        response.getWriter().write(body);
    }

    /**
     * @return the share-token path segment of {@code /api/public/invoices/{token}[/pdf]}
     * when it looks like a token, else null (junk paths are only counted against the
     * per-IP window).
     */
    static String pathToken(String path) {
        String rest = path.substring(PUBLIC_PREFIX.length());
        int lastSlash = rest.lastIndexOf('/');
        String candidate = lastSlash >= 0 ? rest.substring(lastSlash + 1) : rest;
        if (candidate.equals("pdf") && lastSlash >= 0) {
            // /invoices/{token}/pdf -> the segment before "pdf"
            // lastSlash >= 0 is required: path "/api/public/pdf" yields candidate "pdf"
            // with lastSlash == -1, and substring(0, -1) would throw from inside the
            // filter chain (unauthenticated 500).
            String withoutPdf = rest.substring(0, lastSlash);
            int s = withoutPdf.lastIndexOf('/');
            candidate = s >= 0 ? withoutPdf.substring(s + 1) : withoutPdf;
        }
        if (candidate.length() < 22 || candidate.length() > 64) {
            return null;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char ch = candidate.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '-' || ch == '_';
            if (!ok) {
                return null;
            }
        }
        return candidate;
    }

    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustForwardedFor) {
            return remote;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff == null || xff.isBlank()) {
            return remote;
        }
        // Last entry = the one appended by the nearest trusted proxy (see class javadoc).
        String[] hops = xff.split(",");
        String last = hops[hops.length - 1].trim();
        return last.isEmpty() ? remote : last;
    }

    private boolean allow(Map<String, Window> windows, String key, int limit, int maxKeys) {
        long now = System.currentTimeMillis();
        maybeSweep(windows, now);

        Window window;
        if (!windows.containsKey(key) && windows.size() >= maxKeys) {
            // Table at its memory cap: fold new keys into one shared window so the
            // limiter stays bounded instead of growing without limit.
            window = windows.compute(OVERFLOW_KEY, (k, existing) ->
                    existing == null || now - existing.startMs >= WINDOW_MS ? new Window(now) : existing);
        } else {
            window = windows.compute(key, (k, existing) ->
                    existing == null || now - existing.startMs >= WINDOW_MS ? new Window(now) : existing);
        }

        long count = window.count.incrementAndGet();
        if (count > limit) {
            // Never log the key itself: for token windows it would echo (part of) the share token.
            log.debug("Public rate limit hit on {}", key.startsWith("tk:") ? "token window" : "ip window");
            return false;
        }
        return true;
    }

    private void maybeSweep(Map<String, Window> windows, long now) {
        long last = lastSweep.get();
        if (now - last > WINDOW_MS && lastSweep.compareAndSet(last, now)) {
            sweep(windows, now);
        }
    }

    private void sweep(Map<String, Window> windows, long now) {
        Iterator<Map.Entry<String, Window>> it = windows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Window> e = it.next();
            if (now - e.getValue().startMs >= WINDOW_MS) {
                it.remove();
            }
        }
    }

    private static final class Window {
        private final long startMs;
        private final AtomicLong count = new AtomicLong();

        private Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
