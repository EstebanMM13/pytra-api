package com.estebanmm13.pytra_api.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-instance abuse protection for the public auth endpoints: at most {@value #MAX_REQUESTS}
 * POSTs per client IP per endpoint in each fixed one-minute window; extra requests get 429.
 * <p>
 * Client IP: {@code request.getRemoteAddr()}. With {@code server.forward-headers-strategy: framework}
 * Spring's ForwardedHeaderFilter runs first (highest precedence) and makes it return the left-most
 * X-Forwarded-For entry set behind Railway's proxy. That entry can be supplied by the client, so this
 * slows down casual abuse but is not a hard guarantee.
 * <p>
 * Registered as a plain servlet filter (lowest precedence), i.e. after the Spring Security chain, so CORS
 * headers are already on the response and the browser can read the 429.
 */
@Slf4j
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    static final int MAX_REQUESTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/forgot-password"
    );

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public AuthRateLimitFilter() {
        this(Clock.systemUTC());
    }

    AuthRateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String key = request.getRequestURI() + "|" + request.getRemoteAddr();
        if (!tryAcquire(key)) {
            log.warn("Rate limit exceeded on {}", request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":429,\"message\":\"Too many requests\",\"fieldErrors\":{},\"timestamp\":\""
                    + LocalDateTime.now(clock) + "\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    boolean tryAcquire(String key) {
        long now = clock.millis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> window.isExpired(now));
        }
        Window window = windows.compute(key, (k, current) ->
                current == null || current.isExpired(now) ? new Window(now, 1) : current.increment());
        return window.count() <= MAX_REQUESTS;
    }

    private record Window(long startMillis, int count) {
        boolean isExpired(long now) {
            return now - startMillis >= WINDOW.toMillis();
        }

        Window increment() {
            return new Window(startMillis, count + 1);
        }
    }
}
