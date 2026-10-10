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
 *   <li>The public auth endpoints ({@link #PUBLIC_AUTH_PATHS}, exact matches) are allowed: they never act on
 *   the current principal, so a demo token grants nothing an anonymous caller doesn't already have (and a
 *   stale demo header must not break a real login).</li>
 *   <li>{@code /api/v1/users/me/export} and anything below it is blocked even for GET: it consumes the per-user
 *   export rate limit, which every demo visitor shares, and a bulk download is not browsing.</li>
 * </ul>
 * Paths are compared on {@link RequestPaths#pathWithinApplication} (decoded and normalized by the container),
 * never on the raw request URI, so encoded, {@code ..} or {@code ;} variants cannot reach an exemption.
 */
@Component
public class DemoReadOnlyFilter extends OncePerRequestFilter {

    /** Stable message clients can rely on (the HTTP status is 403). */
    public static final String CODE = "DEMO_READ_ONLY";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/exchange-code",
            "/api/v1/auth/demo"
    );
    private static final String BLOCKED_READ_PREFIX = "/api/v1/users/me/export";

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

    static boolean isAllowed(HttpServletRequest request) {
        String path = RequestPaths.pathWithinApplication(request);
        if (PUBLIC_AUTH_PATHS.contains(path)) {
            return true;
        }
        return SAFE_METHODS.contains(request.getMethod().toUpperCase()) && !path.startsWith(BLOCKED_READ_PREFIX);
    }
}
