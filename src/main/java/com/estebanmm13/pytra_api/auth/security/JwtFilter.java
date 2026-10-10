package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
@Order(2)
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final DemoMode demoMode;

    @Override
    protected void doFilterInternal(
            @NotNull HttpServletRequest request,
            @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        final String jwt = authHeader.substring(7);

        final String username;
        final Long userId;
        final Instant issuedAt;
        final boolean demo;
        final List<GrantedAuthority> authorities;
        try {
            username = jwtService.getUserName(jwt);
            userId = jwtService.getUserId(jwt);
            issuedAt = jwtService.getIssuedAt(jwt);
            demo = jwtService.isDemo(jwt);
            // Use the explicit authorities carried in the JWT
            authorities = jwtService.getAuthorities(jwt);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        // Kill switch: once demo mode is disabled (or points to another account), outstanding demo tokens stop working.
        if (demo && !demoMode.isDemoUser(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        // A validly signed token of a deleted account must not authenticate anything (stays anonymous -> 401).
        if (username != null && userId != null && SecurityContextHolder.getContext().getAuthentication() == null
                && userRepository.existsById(userId)) {
            AuthenticatedUser authenticatedUser = new AuthenticatedUser(username, userId, issuedAt, demo);

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    authenticatedUser, null, authorities
            );
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }
        filterChain.doFilter(request, response);
    }
}
