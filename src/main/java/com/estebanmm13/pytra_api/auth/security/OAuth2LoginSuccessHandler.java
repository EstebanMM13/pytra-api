package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.auth.model.PytraOidcUser;
import com.estebanmm13.pytra_api.auth.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final ExchangeCodeIssuer exchangeCodeIssuer;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        PytraOidcUser pytraOidcUser = (PytraOidcUser) authentication.getPrincipal();
        User user = pytraOidcUser.getDomainUser();

        String rawCode = exchangeCodeIssuer.issueFor(user);

        response.sendRedirect(frontendUrl + "/oauth-callback?code=" + rawCode);
    }
}
