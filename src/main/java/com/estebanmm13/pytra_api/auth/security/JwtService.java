package com.estebanmm13.pytra_api.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class JwtService {

    /** Everything a verified token says about its session. */
    public record TokenClaims(
            String username,
            Long userId,
            Instant issuedAt,
            boolean demo,
            List<GrantedAuthority> authorities) {
    }

    /** Claim marking a read-only demo session (see DemoReadOnlyFilter). */
    public static final String DEMO_CLAIM = "demo";
    /** Demo sessions are short-lived and never refreshed: the client asks for a new one instead. */
    public static final Duration DEMO_TOKEN_TTL = Duration.ofHours(2);

    @Value("${JWT_SECRET}")
    private String SECRET_KEY;

    @Value("${JWT_EXPIRATION}")
    private Long EXPIRATION_TIME;

    public String generateToken(Map<String, Object> extraClaims, String subject) {
        return generateToken(extraClaims, subject, EXPIRATION_TIME);
    }

    private String generateToken(Map<String, Object> extraClaims, String subject, long expirationMillis) {
        return Jwts.builder()
                .setClaims(extraClaims).setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256).compact();
    }

    /**
     * Verifies the signature and expiry once and reads every claim from that result.
     *
     * @throws RuntimeException when the token is invalid or lacks the claims of a Pytra session;
     *                          callers must treat that as unauthenticated
     */
    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser().verifyWith(getSignInKey()).build().parseSignedClaims(token).getPayload();
        Date issuedAt = claims.getIssuedAt();
        String role = claims.get("role", String.class);
        return new TokenClaims(
                claims.getSubject(),
                ((Number) claims.get("userId")).longValue(),
                issuedAt != null ? issuedAt.toInstant() : null,
                Boolean.TRUE.equals(claims.get(DEMO_CLAIM, Boolean.class)),
                role != null ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) : List.of());
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateTokenWithRole(String subject, String role, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", userId);
        return generateToken(claims, subject);
    }

    /** Read-only demo token: always role USER, flagged with {@link #DEMO_CLAIM}, valid {@link #DEMO_TOKEN_TTL}. */
    public String generateDemoToken(String subject, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "USER");
        claims.put("userId", userId);
        claims.put(DEMO_CLAIM, true);
        return generateToken(claims, subject, DEMO_TOKEN_TTL.toMillis());
    }
}
