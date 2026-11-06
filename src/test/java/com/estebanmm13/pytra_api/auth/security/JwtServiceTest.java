package com.estebanmm13.pytra_api.auth.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.RequiredTypeException;
import io.jsonwebtoken.security.Keys;
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

        assertThat(jwt.parse(token).username()).isEqualTo("owner");
        assertThat(jwt.parse(token).userId()).isEqualTo(5L);
        assertThat(jwt.parse(token).authorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
        assertThat(jwt.parse(token).issuedAt()).isBetween(before, Instant.now());
        assertThat(jwt.parse(token).demo()).isFalse();
    }

    @Test
    void demoTokenIsAFlaggedUserSession() {
        JwtService jwt = service(SECRET, 60_000);

        String token = jwt.generateDemoToken("demo", 7L);

        assertThat(jwt.parse(token).demo()).isTrue();
        assertThat(jwt.parse(token).userId()).isEqualTo(7L);
        assertThat(jwt.parse(token).authorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_USER");
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = service(SECRET, -1_000);

        String token = jwt.generateTokenWithRole("owner", "USER", 5L);

        assertThatThrownBy(() -> jwt.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = service(OTHER_SECRET, 60_000).generateTokenWithRole("owner", "ADMIN", 5L);

        assertThatThrownBy(() -> service(SECRET, 60_000).parse(foreign)).isInstanceOf(SignatureException.class);
    }

    @Test
    void tamperedPayloadIsRejected() {
        JwtService jwt = service(SECRET, 60_000);
        String[] parts = jwt.generateTokenWithRole("owner", "USER", 5L).split("\\.");
        // Same header and signature, payload swapped for one claiming ADMIN.
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"role\":\"ADMIN\",\"userId\":5,\"sub\":\"owner\"}".getBytes());

        assertThatThrownBy(() -> jwt.parse(parts[0] + "." + forgedPayload + "." + parts[2]))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void validTokenWithoutUserIdIsRejected() {
        String token = signed(Jwts.builder().subject("owner").claim("role", "USER"));

        assertThatThrownBy(() -> service(SECRET, 60_000).parse(token)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void validTokenWithANonTextRoleIsRejected() {
        String token = signed(Jwts.builder().subject("owner").claim("userId", 5).claim("role", 1));

        assertThatThrownBy(() -> service(SECRET, 60_000).parse(token)).isInstanceOf(RequiredTypeException.class);
    }

    private static String signed(JwtBuilder builder) {
        return builder.signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).compact();
    }
}
