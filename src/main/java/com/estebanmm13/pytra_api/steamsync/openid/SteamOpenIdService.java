package com.estebanmm13.pytra_api.steamsync.openid;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Steam usa un perfil simplificado y sin estado de OpenID 2.0 (sin discovery XRDS,
 * sin asociación Diffie-Hellman) — por eso no hace falta ninguna librería de
 * cliente OpenID (openid4java lleva sin mantenimiento real desde 2015). Verificar
 * la respuesta es solo repetir los mismos parámetros firmados en un POST con
 * openid.mode=check_authentication; si Steam responde "is_valid:true", son
 * genuinos.
 */
@Service
public class SteamOpenIdService {

    private static final String STEAM_OPENID_ENDPOINT = "https://steamcommunity.com/openid/login";
    private static final Pattern CLAIMED_ID_PATTERN = Pattern.compile("^https://steamcommunity\\.com/openid/id/(\\d+)$");

    private final RestClient restClient = RestClient.builder()
            .requestFactory(timeoutRequestFactory())
            .build();

    /** 5s connect / 15s read so a slow Steam can never hang the callback request. */
    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return requestFactory;
    }

    public String buildLoginRedirectUrl(String returnTo, String realm) {
        String params = "openid.ns=" + encode("http://specs.openid.net/auth/2.0")
                + "&openid.mode=" + encode("checkid_setup")
                + "&openid.return_to=" + encode(returnTo)
                + "&openid.realm=" + encode(realm)
                + "&openid.identity=" + encode("http://specs.openid.net/auth/2.0/identifier_select")
                + "&openid.claimed_id=" + encode("http://specs.openid.net/auth/2.0/identifier_select");
        return STEAM_OPENID_ENDPOINT + "?" + params;
    }

    /**
     * Validates a Steam OpenID callback. Besides relaying to check_authentication
     * (signature check), it binds the assertion to THIS callback: the endpoint must
     * be Steam's, return_to must point at our callback URL, both fields must be
     * covered by the signature, and the state/client params we received must be the
     * ones embedded in the signed return_to.
     *
     * @param expectedCallbackUrl absolute callback URL without query string
     */
    public boolean verify(Map<String, String> openIdParams, String expectedCallbackUrl) {
        if (!STEAM_OPENID_ENDPOINT.equals(openIdParams.get("openid.op_endpoint"))) {
            return false;
        }

        String returnTo = openIdParams.get("openid.return_to");
        if (returnTo == null
                || !(returnTo.equals(expectedCallbackUrl) || returnTo.startsWith(expectedCallbackUrl + "?"))) {
            return false;
        }

        String signed = openIdParams.get("openid.signed");
        List<String> signedFields = signed == null ? List.of() : Arrays.asList(signed.split(","));
        if (!signedFields.contains("return_to") || !signedFields.contains("op_endpoint")) {
            return false;
        }

        if (!Objects.equals(returnToParam(returnTo, "state"), openIdParams.get("state"))
                || !Objects.equals(returnToParam(returnTo, "client"), openIdParams.get("client"))) {
            return false;
        }

        return checkAuthentication(openIdParams);
    }

    /** Reads (decoded) a query param from a return_to URL. Only trust it after {@link #verify}. */
    public String returnToParam(String returnTo, String name) {
        if (returnTo == null) return null;
        UriComponents components = UriComponentsBuilder.fromUriString(returnTo).build();
        String value = components.getQueryParams().getFirst(name);
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean checkAuthentication(Map<String, String> openIdParams) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        openIdParams.forEach(body::add);
        body.set("openid.mode", "check_authentication");

        String response = restClient.post()
                .uri(STEAM_OPENID_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(String.class);

        return response != null && response.contains("is_valid:true");
    }

    public String extractSteamId64(String claimedId) {
        if (claimedId == null) return null;
        Matcher matcher = CLAIMED_ID_PATTERN.matcher(claimedId);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
