package com.insideinvoice.security;

import com.insideinvoice.auth.entity.PersonalAccessToken;
import com.insideinvoice.auth.service.PersonalAccessTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String PAT_PREFIX = "pat_";

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final PersonalAccessTokenService personalAccessTokenService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String bearer = extractBearer(request);

            if (StringUtils.hasText(bearer) && bearer.startsWith(PAT_PREFIX)) {
                // Personal Access Token: authenticate as the owning user. The
                // resolve also RECORDS the hit (count + who called), so pass the
                // calling client's address and User-Agent along (V52).
                personalAccessTokenService.resolve(bearer, clientIp(request), userAgent(request)).ifPresent(pat -> {
                    try {
                        UserDetails userDetails = customUserDetailsService.loadUserById(pat.getUserId());
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } catch (Exception e) {
                        log.warn("Could not authenticate PAT for user {}", pat.getUserId(), e);
                    }
                });
            } else if (StringUtils.hasText(bearer)) {
                String jwt = bearer;
                if (jwtTokenProvider.validateToken(jwt)) {
                    Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
                    UserDetails userDetails = customUserDetailsService.loadUserById(userId);

                    // Session was revoked (logout bumps token_version): treat as anonymous
                    // rather than authenticating with a token the user has already ended.
                    if (userDetails instanceof UserPrincipal principal
                            && principal.getTokenVersion() != jwtTokenProvider.getTokenVersion(jwt)) {
                        log.warn("Rejected revoked JWT for user {}", userId);
                        filterChain.doFilter(request, response);
                        return;
                    }

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    // Try cookie fallback
                    Cookie[] cookies = request.getCookies();
                    if (cookies != null) {
                        for (Cookie cookie : cookies) {
                            if ("jwt".equals(cookie.getName())) {
                                String token = cookie.getValue();
                                if (jwtTokenProvider.validateToken(token)) {
                                    Long userId = jwtTokenProvider.getUserIdFromToken(token);
                                    UserDetails userDetails = customUserDetailsService.loadUserById(userId);
                                    if (userDetails instanceof UserPrincipal principal
                                            && principal.getTokenVersion() != jwtTokenProvider.getTokenVersion(token)) {
                                        log.warn("Rejected revoked JWT (cookie) for user {}", userId);
                                        filterChain.doFilter(request, response);
                                        return;
                                    }
                                    UsernamePasswordAuthenticationToken authentication =
                                            new UsernamePasswordAuthenticationToken(
                                                    userDetails, null, userDetails.getAuthorities());
                                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                    SecurityContextHolder.getContext().setAuthentication(authentication);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Could not set user authentication in security context", e);
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearer(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Calling client's address for the PAT usage record: the first hop of
     * X-Forwarded-For when the app sits behind the platform proxy (Railway /
     * nginx append the client address there), the socket address otherwise.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    /** User-Agent of the calling client — "which integration" for the PAT screen. */
    private static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        return ua == null ? null : ua.trim();
    }
}
