package com.estebanmm13.pytra_api.steamsync.controller;

import com.estebanmm13.pytra_api.auth.model.User;
import com.estebanmm13.pytra_api.auth.repository.UserRepository;
import com.estebanmm13.pytra_api.auth.security.ClientRedirects;
import com.estebanmm13.pytra_api.auth.security.CurrentUserResolver;
import com.estebanmm13.pytra_api.auth.security.ExchangeCodeIssuer;
import com.estebanmm13.pytra_api.config.UrlNormalizer;
import com.estebanmm13.pytra_api.error.InvalidTokenException;
import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.steamsync.client.SteamWebApiClient;
import com.estebanmm13.pytra_api.steamsync.openid.SteamOpenIdService;
import com.estebanmm13.pytra_api.steamsync.service.SteamLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * "Conectar Steam" para un usuario que YA tiene sesión en Pytra — no es un
 * método de login alternativo (a diferencia de Google). El userId viaja a
 * través de la redirección externa a steamcommunity.com dentro de un token
 * de estado de un solo uso (SteamLinkState), nunca en claro, para que nadie
 * pueda enlazar su propia cuenta Steam a la sesión de otro usuario.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/integrations/steam")
@RequiredArgsConstructor
public class SteamOpenIdController {

    private static final String CALLBACK_PATH = "/api/v1/integrations/steam/callback";

    /** Fixed error values sent to the client callback (the client maps them to messages). */
    static final String LINK_FAILED_ERROR = "steam_link_failed";
    static final String ALREADY_LINKED_ERROR = "steam_account_already_linked";
    static final String SYNC_IN_PROGRESS_ERROR = "steam_sync_in_progress";

    private String publicApiUrl;

    private final CurrentUserResolver currentUserResolver;
    private final SteamOpenIdService steamOpenIdService;
    private final SteamLinkService steamLinkService;
    private final SteamWebApiClient steamWebApiClient;
    private final ExchangeCodeIssuer exchangeCodeIssuer;
    private final UserRepository userRepository;
    private final ClientRedirects clientRedirects;

    @Value("${app.public-api-url}")
    void setPublicApiUrl(String publicApiUrl) {
        this.publicApiUrl = UrlNormalizer.normalizeBaseUrl(publicApiUrl);
    }

    /**
     * Paso 1, autenticado vía XHR (lleva el JWT en el header Authorization,
     * como cualquier otra llamada). Emite el token de estado de un solo uso
     * para el userId actual. El frontend lo recoge y lo pasa como query param
     * en la navegación de página completa a /login — que SÍ puede llevar
     * query params, a diferencia del header Authorization, que un
     * window.location.href normal nunca envía.
     */
    @PostMapping("/connect-token")
    public ResponseEntity<Map<String, String>> connectToken() {
        Long userId = currentUserResolver.getCurrentUserId();
        String state = steamLinkService.createLinkState(userId);
        return ResponseEntity.ok(Map.of("token", state));
    }

    /**
     * Paso 2, navegación de página completa (no XHR) hacia Steam. Pública a
     * propósito: el navegador no adjunta el header Authorization en una
     * navegación normal, así que la identidad del usuario viaja en el
     * parámetro `state` ya emitido por /connect-token, no por sesión.
     */
    @GetMapping("/login")
    public ResponseEntity<Void> login(@RequestParam String state,
                                      @RequestParam(required = false) String client) {
        // Encoded so a crafted `state` cannot inject extra query params into return_to.
        UriComponentsBuilder returnToBuilder = UriComponentsBuilder
                .fromUriString(callbackUrl())
                .queryParam("state", state);
        if (ClientRedirects.isMobileClient(client)) {
            // Embedded in openid.return_to, which Steam signs; the callback reads it back from
            // the verified return_to (see SteamOpenIdService#verify), not from the raw query.
            returnToBuilder.queryParam("client", ClientRedirects.MOBILE_CLIENT);
        }
        String returnTo = returnToBuilder.build().encode().toUriString();
        String redirectUrl = steamOpenIdService.buildLoginRedirectUrl(returnTo, publicApiUrl);

        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }

    /**
     * Always ends in a redirect to the client callback, never in a raw error page on the API
     * domain: any failure (bad state, failed verification, Steam down, account taken) maps to a
     * fixed {@code error} value. {@code next=steam} makes the client land back on /steam.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam Map<String, String> allParams) {
        // Unverified hint, used only for error redirects before verification succeeds.
        // It can only select between the two configured targets (web / app), never a free URL.
        boolean mobile = ClientRedirects.isMobileClient(allParams.get("client"));
        try {
            Long userId = steamLinkService.consumeLinkState(allParams.get("state"));

            if (!steamOpenIdService.verify(allParams, callbackUrl())) {
                return redirectToFrontendWithError(mobile, LINK_FAILED_ERROR);
            }
            // From here on, trust only the client embedded in the signed return_to.
            mobile = ClientRedirects.isMobileClient(
                    steamOpenIdService.returnToParam(allParams.get("openid.return_to"), "client"));

            String steamId64 = steamOpenIdService.extractSteamId64(allParams.get("openid.claimed_id"));
            if (steamId64 == null) {
                return redirectToFrontendWithError(mobile, LINK_FAILED_ERROR);
            }

            steamLinkService.upsertLink(userId, steamId64, fetchPersonaName(steamId64));

            User user = userRepository.findById(userId).orElseThrow();
            String rawCode = exchangeCodeIssuer.issueFor(user);

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(clientRedirects.callbackWithCode(mobile, rawCode, ClientRedirects.NEXT_STEAM)))
                    .build();

        } catch (InvalidTokenException e) {
            log.warn("Steam link callback rejected: {}", e.getMessage());
            return redirectToFrontendWithError(mobile, LINK_FAILED_ERROR);
        } catch (SteamIntegrationException e) {
            log.warn("Steam link callback rejected: {}", e.getCode());
            return redirectToFrontendWithError(mobile, switch (e.getCode()) {
                case SteamIntegrationException.ACCOUNT_ALREADY_LINKED -> ALREADY_LINKED_ERROR;
                // Switching to another Steam account while a sync of the current one is running.
                case SteamIntegrationException.SYNC_IN_PROGRESS -> SYNC_IN_PROGRESS_ERROR;
                default -> LINK_FAILED_ERROR;
            });
        } catch (DataIntegrityViolationException e) {
            // Lost a race on uq_steam_links_steam_id against another user linking the same account.
            log.warn("Steam link callback rejected: Steam account already linked (concurrent)");
            return redirectToFrontendWithError(mobile, ALREADY_LINKED_ERROR);
        } catch (Exception e) {
            // Class name only: HTTP-layer messages may include Steam URLs.
            log.warn("Steam link callback failed: {}", e.getClass().getSimpleName());
            return redirectToFrontendWithError(mobile, LINK_FAILED_ERROR);
        }
    }

    /** Persona name is cosmetic: never let a missing/invalid API key or a Steam outage break linking. */
    private String fetchPersonaName(String steamId64) {
        try {
            return steamWebApiClient.getPersonaName(steamId64);
        } catch (Exception e) {
            log.info("Steam persona name unavailable: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private String callbackUrl() {
        return publicApiUrl + CALLBACK_PATH;
    }

    private ResponseEntity<Void> redirectToFrontendWithError(boolean mobile, String error) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(clientRedirects.callbackWithError(mobile, error, ClientRedirects.NEXT_STEAM)))
                .build();
    }
}
