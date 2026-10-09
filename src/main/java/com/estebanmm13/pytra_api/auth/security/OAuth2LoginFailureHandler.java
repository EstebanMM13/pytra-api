package com.estebanmm13.pytra_api.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Sends a failed Google login (user cancelled, invalid state, provider error...)
 * back to the client's callback page with an error flag, instead of Spring's
 * default "/login?error" on the API host, which does not exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    static final String GENERIC_ERROR = "google_login_failed";

    private final ClientRedirects clientRedirects;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        // Log only the OAuth2 error code: the message may echo provider/request input.
        String reason = exception instanceof OAuth2AuthenticationException oauth2Exception
                ? oauth2Exception.getError().getErrorCode()
                : exception.getClass().getSimpleName();
        log.warn("Google login failed: {}", reason);
        boolean mobile = MobileFlagAuthorizationRequestRepository.isMobileFlow(request);
        response.sendRedirect(clientRedirects.callbackWithError(mobile, clientErrorFor(reason)));
    }

    /** Maps the failure to a fixed client error value; exception text is never forwarded. */
    static String clientErrorFor(String reason) {
        return GoogleOidcUserService.REGISTRATION_CLOSED_ERROR_CODE.equals(reason)
                ? GoogleOidcUserService.REGISTRATION_CLOSED_ERROR_CODE
                : GENERIC_ERROR;
    }
}
