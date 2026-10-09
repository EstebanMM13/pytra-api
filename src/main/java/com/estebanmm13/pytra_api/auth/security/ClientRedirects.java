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

    /** {@code next} value for flows that should land on the Steam page (the Steam account link). */
    public static final String NEXT_STEAM = "steam";

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
        return build(mobile, "code", code, null);
    }

    public String callbackWithError(boolean mobile, String error) {
        return build(mobile, "error", error, null);
    }

    /**
     * Same as {@link #callbackWithCode(boolean, String)} plus a {@code next} hint telling the client
     * where to land afterwards. Callers pass a fixed server-side constant (e.g. {@link #NEXT_STEAM}),
     * and the client only honours whitelisted values, so this is not an open redirect either.
     */
    public String callbackWithCode(boolean mobile, String code, String next) {
        return build(mobile, "code", code, next);
    }

    public String callbackWithError(boolean mobile, String error, String next) {
        return build(mobile, "error", error, next);
    }

    private String build(boolean mobile, String param, String value, String next) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(mobile ? mobileCallbackUrl : webCallbackUrl)
                .queryParam(param, value);
        if (next != null) {
            builder.queryParam("next", next);
        }
        return builder.build()
                .encode()
                .toUriString();
    }
}
