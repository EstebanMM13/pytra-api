package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.account.model.PersonalAccessToken;
import com.estebanmm13.pytra_api.account.service.PersonalAccessTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates {@code Authorization: Bearer pytra_pat_...} (personal access tokens), but ONLY for
 * {@code GET} {@value #EXPORT_PATH}: tokens are read-only credentials for the Obsidian sync. On any other
 * request a token authenticates nothing, and JwtFilter skips token-shaped values, so the request stays
 * anonymous and protected endpoints answer 401. An unknown or revoked token also stays anonymous (401).
 * <p>
 * Runs in the Spring Security chain right before JwtFilter. The path is compared on
 * {@link RequestPaths#pathWithinApplication} (decoded and normalized by the container), never on the raw URI.
 */
@Component
@RequiredArgsConstructor
public class PersonalAccessTokenFilter extends OncePerRequestFilter {

    public static final String EXPORT_PATH = "/api/v1/export/obsidian";
    /** Marker authority of token-authenticated requests; grants no role. */
    public static final String TOKEN_AUTHORITY = "PERSONAL_ACCESS_TOKEN";
    private static final String BEARER = "Bearer ";

    private final PersonalAccessTokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER) && isTokenEndpoint(request)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String bearerValue = authHeader.substring(BEARER.length());
            if (PersonalAccessTokenService.looksLikeToken(bearerValue)) {
                tokenService.authenticate(bearerValue).ifPresent(token -> authenticate(request, token));
            }
        }
        filterChain.doFilter(request, response);
    }

    static boolean isTokenEndpoint(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                && EXPORT_PATH.equals(RequestPaths.pathWithinApplication(request));
    }

    private static void authenticate(HttpServletRequest request, PersonalAccessToken token) {
        PersonalAccessTokenPrincipal principal = new PersonalAccessTokenPrincipal(
                token.getUser().getUsername(), token.getUser().getId(), token.getId());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(TOKEN_AUTHORITY)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
