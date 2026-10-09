package com.estebanmm13.pytra_api.steamsync.openid;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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

    private final RestClient restClient = RestClient.create();

    public String buildLoginRedirectUrl(String returnTo, String realm) {
        String params = "openid.ns=" + encode("http://specs.openid.net/auth/2.0")
                + "&openid.mode=" + encode("checkid_setup")
                + "&openid.return_to=" + encode(returnTo)
                + "&openid.realm=" + encode(realm)
                + "&openid.identity=" + encode("http://specs.openid.net/auth/2.0/identifier_select")
                + "&openid.claimed_id=" + encode("http://specs.openid.net/auth/2.0/identifier_select");
        return STEAM_OPENID_ENDPOINT + "?" + params;
    }

    public boolean verify(Map<String, String> openIdParams) {
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
