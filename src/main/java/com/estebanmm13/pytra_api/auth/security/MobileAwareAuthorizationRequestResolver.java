package com.estebanmm13.pytra_api.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Wraps the default resolver so that a login started with
 * {@code /oauth2/authorization/google?client=android} is remembered as a mobile
 * flow. The flag is stored as an attribute of the OAuth2AuthorizationRequest
 * itself, which Spring Security persists (bound to the OAuth2 {@code state})
 * until Google redirects back to {@code /login/oauth2/code/google}.
 */
public class MobileAwareAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    public static final String MOBILE_ATTRIBUTE = "pytra_mobile_client";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public MobileAwareAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                "/oauth2/authorization");
        // Always show Google's account chooser instead of silently reusing the
        // account the browser is already signed in with.
        this.delegate.setAuthorizationRequestCustomizer(builder ->
                builder.additionalParameters(params -> params.put("prompt", "select_account")));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return markIfMobile(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return markIfMobile(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest markIfMobile(HttpServletRequest request,
                                                    OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null || !ClientRedirects.isMobileClient(request.getParameter("client"))) {
            return authorizationRequest;
        }
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .attributes(attributes -> attributes.put(MOBILE_ATTRIBUTE, Boolean.TRUE))
                .build();
    }

    public static boolean isMobile(OAuth2AuthorizationRequest authorizationRequest) {
        return authorizationRequest != null
                && Boolean.TRUE.equals(authorizationRequest.getAttribute(MOBILE_ATTRIBUTE));
    }
}
