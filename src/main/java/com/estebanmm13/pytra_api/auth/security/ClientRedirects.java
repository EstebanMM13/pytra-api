package com.estebanmm13.pytra_api.auth.security;

import com.estebanmm13.pytra_api.config.UrlNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Builds the final redirect after an external login/link flow (Google, Steam).
 * The destination is always taken from configuration: the web SPA callback or
 * the Android app's custom-scheme callback. Request input only selects WHICH of
 * those two configured targets is used, so it can never cause an open redirect.
 */
@Component
public class ClientRedirects {

    /** Value of the {@code client} query param that marks a flow started from the Android app. */
    public static final String MOBILE_CLIENT = "android";

    private final String webCallbackUrl;
    private final String mobileCallbackUrl;

    public ClientRedirects(@Value("${app.frontend-url}") String frontendUrl,
                           @Value("${app.mobile-redirect-uri}") String mobileRedirectUri) {
        this.webCallbackUrl = UrlNormalizer.normalizeBaseUrl(frontendUrl) + "/oauth-callback";
        this.mobileCallbackUrl = UrlNormalizer.normalizeBaseUrl(mobileRedirectUri);
    }

    public static boolean isMobileClient(String client) {
        return MOBILE_CLIENT.equals(client);
    }

    public String callbackWithCode(boolean mobile, String code) {
        return build(mobile, "code", code);
    }

    public String callbackWithError(boolean mobile, String error) {
        return build(mobile, "error", error);
    }

    private String build(boolean mobile, String param, String value) {
        return UriComponentsBuilder.fromUriString(mobile ? mobileCallbackUrl : webCallbackUrl)
                .queryParam(param, value)
                .build()
                .encode()
                .toUriString();
    }
}
