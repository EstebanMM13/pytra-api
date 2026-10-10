package com.estebanmm13.pytra_api.auth.security;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRateLimitFilterTest {

    @Test
    void allowsFiveRequestsPerWindowPerKeyThenResets() {
        MutableClock clock = new MutableClock();
        AuthRateLimitFilter filter = new AuthRateLimitFilter(clock);

        for (int i = 0; i < AuthRateLimitFilter.MAX_REQUESTS; i++) {
            assertTrue(filter.tryAcquire("/login|1.1.1.1"));
        }
        assertFalse(filter.tryAcquire("/login|1.1.1.1"));
        assertTrue(filter.tryAcquire("/login|2.2.2.2"), "other IPs are independent");
        assertTrue(filter.tryAcquire("/register|1.1.1.1"), "other endpoints are independent");

        clock.advance(AuthRateLimitFilter.WINDOW);
        assertTrue(filter.tryAcquire("/login|1.1.1.1"));
    }

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String PROXY_ADDR = "10.0.0.1";

    /**
     * One POST through the real ForwardedHeaderFilter (as with forward-headers-strategy: framework) and then the
     * rate limiter, the way Tomcat would build it: servletPath decoded/normalized, requestURI raw.
     */
    private static int post(AuthRateLimitFilter filter, Consumer<MockHttpServletRequest> customizer,
                            AtomicReference<HttpServletRequest> seen) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", LOGIN);
        request.setServletPath(LOGIN);
        request.setRemoteAddr(PROXY_ADDR);
        customizer.accept(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpServlet endpoint = new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) {
                seen.set(req);
                res.setStatus(200);
            }
        };
        new MockFilterChain(endpoint, new ForwardedHeaderFilter(), filter).doFilter(request, response);
        return response.getStatus();
    }

    private static int postTimes(AuthRateLimitFilter filter, int times, Consumer<MockHttpServletRequest> customizer)
            throws Exception {
        int status = 0;
        for (int i = 0; i < times; i++) {
            status = post(filter, customizer, new AtomicReference<>());
        }
        return status;
    }

    @Test
    void forwardedPrefixDoesNotBypassTheLimit() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(new MutableClock());
        AtomicReference<HttpServletRequest> seen = new AtomicReference<>();
        Consumer<MockHttpServletRequest> withPrefix = request -> request.addHeader("X-Forwarded-Prefix", "/evil");

        assertThat(post(filter, withPrefix, seen)).isEqualTo(200);
        // Sanity check: the header really changes the URI the old exact-URI match looked at.
        assertThat(seen.get().getRequestURI()).isEqualTo("/evil" + LOGIN);

        assertThat(postTimes(filter, AuthRateLimitFilter.MAX_REQUESTS - 1, withPrefix)).isEqualTo(200);
        assertThat(post(filter, withPrefix, seen)).isEqualTo(429);
        assertThat(post(filter, request -> { }, seen)).as("same bucket without the header").isEqualTo(429);
    }

    @Test
    void percentEncodedUriDoesNotBypassTheLimit() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(new MutableClock());
        // Tomcat decodes the servlet path ("/login") but leaves the request URI raw.
        Consumer<MockHttpServletRequest> encoded = request -> request.setRequestURI("/api/v1/auth/%6Cogin");

        assertThat(postTimes(filter, AuthRateLimitFilter.MAX_REQUESTS, encoded)).isEqualTo(200);
        assertThat(post(filter, encoded, new AtomicReference<>())).isEqualTo(429);
    }

    @Test
    void spoofedLeftMostForwardedForEntriesShareTheProxyAppendedBucket() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(new MutableClock());
        int[] attempt = {0};
        // The attacker rotates the entry it controls; Railway's proxy appends the real address last.
        Consumer<MockHttpServletRequest> spoofed = request ->
                request.addHeader("X-Forwarded-For", "198.51.100." + attempt[0]++ + ", 203.0.113.9");

        assertThat(postTimes(filter, AuthRateLimitFilter.MAX_REQUESTS, spoofed)).isEqualTo(200);
        assertThat(post(filter, spoofed, new AtomicReference<>())).isEqualTo(429);

        assertThat(post(filter, request -> request.addHeader("X-Forwarded-For", "203.0.113.10"),
                new AtomicReference<>())).as("another real client is independent").isEqualTo(200);
    }

    @Test
    void clientIpIsTheRightMostForwardedForEntry() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", LOGIN);
        request.setRemoteAddr(PROXY_ADDR);
        assertThat(AuthRateLimitFilter.clientIp(request)).as("no header: socket peer").isEqualTo(PROXY_ADDR);

        request.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2");
        request.addHeader("X-Forwarded-For", "3.3.3.3 , 203.0.113.9 ");
        assertThat(AuthRateLimitFilter.clientIp(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void otherPathsAndMethodsAreNotLimited() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(new MutableClock());
        Consumer<MockHttpServletRequest> exchange = request -> {
            request.setRequestURI("/api/v1/auth/exchange-code");
            request.setServletPath("/api/v1/auth/exchange-code");
        };
        assertThat(postTimes(filter, AuthRateLimitFilter.MAX_REQUESTS + 1, exchange)).isEqualTo(200);
        assertThat(postTimes(filter, AuthRateLimitFilter.MAX_REQUESTS + 1, request -> request.setMethod("GET")))
                .isEqualTo(200);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
