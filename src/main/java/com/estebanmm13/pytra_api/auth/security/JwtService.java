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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${JWT_SECRET}")
    private String SECRET_KEY;

    @Value("${JWT_EXPIRATION}")
    private Long EXPIRATION_TIME;

    public String generateToken(Map<String, Object> extraClaims, String subject) {
        return Jwts.builder()
                .setClaims(extraClaims).setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256).compact();
    }

    public String getUserName(String token) {
        return getClaim(token, Claims::getSubject);
    }

    public Long getUserId(String token) {
        return getClaim(token, claims -> ((Number) claims.get("userId")).longValue());
    }

    public <T> T getClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaims(String token) {
        return Jwts.parser().verifyWith(getSignInKey()).build().parseSignedClaims(token).getPayload();
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

    public String extractRole(String token) {
        return getClaim(token, claims -> claims.get("role", String.class));
    }

    public List<GrantedAuthority> getAuthorities(String token) {
        String role = extractRole(token);
        if (role != null) {
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        }
        return List.of(); // Lista vacía si no hay rol
    }



}
