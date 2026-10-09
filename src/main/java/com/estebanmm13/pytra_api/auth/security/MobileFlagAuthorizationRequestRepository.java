package com.estebanmm13.pytra_api.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Same storage as Spring's default (HTTP session, which is still created for the
 * OAuth2 round trip even with a STATELESS security context). When the callback
 * consumes the stored authorization request, its mobile flag is copied to a
 * request attribute so the success/failure handlers of that same request can
 * pick the right redirect target.
 */
public class MobileFlagAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String MOBILE_REQUEST_ATTRIBUTE =
            MobileFlagAuthorizationRequestRepository.class.getName() + ".MOBILE";

    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request,
                                         HttpServletResponse response) {
        delegate.saveAuthorizationRequest(authorizationRequest, request, response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest removed = delegate.removeAuthorizationRequest(request, response);
        if (MobileAwareAuthorizationRequestResolver.isMobile(removed)) {
            request.setAttribute(MOBILE_REQUEST_ATTRIBUTE, Boolean.TRUE);
        }
        return removed;
    }

    /** True when the OAuth2 callback being processed belongs to a flow started from the Android app. */
    public static boolean isMobileFlow(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(MOBILE_REQUEST_ATTRIBUTE));
    }
}
