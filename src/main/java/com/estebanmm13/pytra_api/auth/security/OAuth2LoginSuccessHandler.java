package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.auth.model.ExchangeCodeToken;
import com.estebanmm13.pytra_api.auth.model.PytraOidcUser;
import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.ExchangeCodeTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final TokenGenerator tokenGenerator;
    private final ExchangeCodeTokenRepository exchangeCodeTokenRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        PytraOidcUser pytraOidcUser = (PytraOidcUser) authentication.getPrincipal();
        User user = pytraOidcUser.getDomainUser();

        String rawCode = tokenGenerator.generateTokenRaw();
        String codeHash = tokenGenerator.hashToken(rawCode);

        ExchangeCodeToken exchangeCodeToken = ExchangeCodeToken.builder()
                .user(user)
                .tokenHash(codeHash)
                .expiresAt(LocalDateTime.now().plusMinutes(1))
                .build();
        exchangeCodeTokenRepository.save(exchangeCodeToken);

        response.sendRedirect("http://localhost:4200/oauth-callback?code=" + rawCode);
    }
}
