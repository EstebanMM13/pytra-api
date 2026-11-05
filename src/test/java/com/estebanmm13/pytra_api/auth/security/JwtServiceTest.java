package com.estebanmm13.pytra_api.auth.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JWTs carry identity, role and issue time; only an unexpired token signed with our key is readable. */
class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    private static JwtService service(String secret, long expirationMillis) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "SECRET_KEY", secret);
        ReflectionTestUtils.setField(service, "EXPIRATION_TIME", expirationMillis);
        return service;
    }

    @Test
    void sessionTokenRoundTripsIdentityRoleAndIssueTime() {
        JwtService jwt = service(SECRET, 60_000);
        Instant before = Instant.now().minusSeconds(1);

        String token = jwt.generateTokenWithRole("owner", "ADMIN", 5L);

        assertThat(jwt.getUserName(token)).isEqualTo("owner");
        assertThat(jwt.getUserId(token)).isEqualTo(5L);
        assertThat(jwt.getAuthorities(token)).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(jwt.getIssuedAt(token)).isBetween(before, Instant.now());
        assertThat(jwt.isDemo(token)).isFalse();
    }

    @Test
    void demoTokenIsAFlaggedUserSession() {
        JwtService jwt = service(SECRET, 60_000);

        String token = jwt.generateDemoToken("demo", 7L);

        assertThat(jwt.isDemo(token)).isTrue();
        assertThat(jwt.getUserId(token)).isEqualTo(7L);
        assertThat(jwt.getAuthorities(token)).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = service(SECRET, -1_000);

        String token = jwt.generateTokenWithRole("owner", "USER", 5L);

        assertThatThrownBy(() -> jwt.getUserName(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = service(OTHER_SECRET, 60_000).generateTokenWithRole("owner", "ADMIN", 5L);

        assertThatThrownBy(() -> service(SECRET, 60_000).getUserName(foreign)).isInstanceOf(SignatureException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtService jwt = service(SECRET, 60_000);
        String[] parts = jwt.generateTokenWithRole("owner", "USER", 5L).split("\\.");
        // Same header and signature, payload swapped for one claiming ADMIN.
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"role\":\"ADMIN\",\"userId\":5,\"sub\":\"owner\"}".getBytes());

        assertThatThrownBy(() -> jwt.getUserName(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(SignatureException.class);
    }
}
