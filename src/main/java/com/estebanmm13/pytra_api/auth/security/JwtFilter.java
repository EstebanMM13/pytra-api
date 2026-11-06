package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.account.service.PersonalAccessTokenService;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

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
        // Personal access tokens are not JWTs: PersonalAccessTokenFilter handles them (or leaves them anonymous).
        if (PersonalAccessTokenService.looksLikeToken(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        final JwtService.TokenClaims claims;
        try {
            claims = jwtService.parse(jwt);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        // Kill switch: once demo mode is disabled (or points to another account), outstanding demo tokens stop working.
        if (claims.demo() && !demoMode.isDemoUser(claims.userId())) {
            filterChain.doFilter(request, response);
            return;
        }

        // A validly signed token of a deleted account, or one issued before a password reset, must not
        // authenticate anything (stays anonymous -> 401). One query covers both.
        if (claims.username() != null && claims.userId() != null && SecurityContextHolder.getContext().getAuthentication() == null
                && userRepository.isTokenStillValid(claims.userId(), claims.issuedAt())) {
            AuthenticatedUser authenticatedUser = new AuthenticatedUser(claims.username(), claims.userId(), claims.issuedAt(), claims.demo());

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    authenticatedUser, null, claims.authorities()
            );
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }
        filterChain.doFilter(request, response);
    }
}
