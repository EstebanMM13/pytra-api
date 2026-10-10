package com.estebanmm13.pytra_api.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Server-side guarantee of the read-only demo: a request authenticated with a demo token may only use
 * safe methods (GET/HEAD/OPTIONS); anything else gets 403 {@value #CODE}. Runs inside the Spring Security
 * chain right after JwtFilter, so it sees the demo principal and CORS headers are already set.
 * <p>
 * Exceptions:
 * <ul>
 *   <li>{@code /api/v1/auth/**} is allowed: those endpoints are public and never act on the current
 *   principal, so a demo token grants nothing an anonymous caller doesn't already have (and a stale demo
 *   header must not break a real login).</li>
 *   <li>{@code GET /api/v1/users/me/export} is blocked: it consumes the per-user export rate limit, which
 *   every demo visitor shares, and a bulk download is not browsing.</li>
 * </ul>
 */
@Component
public class DemoReadOnlyFilter extends OncePerRequestFilter {

    /** Stable message clients can rely on (the HTTP status is 403). */
    public static final String CODE = "DEMO_READ_ONLY";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String PUBLIC_AUTH_PREFIX = "/api/v1/auth/";
    private static final Set<String> BLOCKED_READS = Set.of("/api/v1/users/me/export");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (isDemoSession() && !isAllowed(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":403,\"message\":\"" + CODE + "\",\"fieldErrors\":{},\"timestamp\":\""
                    + LocalDateTime.now() + "\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isDemoSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && user.isDemo();
    }

    // Spring Security's StrictHttpFirewall has already rejected non-normalized paths ("..", "//", ";"),
    // so a prefix/exact match on the URI is reliable here.
    static boolean isAllowed(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith(PUBLIC_AUTH_PREFIX)) {
            return true;
        }
        return SAFE_METHODS.contains(request.getMethod().toUpperCase()) && !BLOCKED_READS.contains(path);
    }
}
