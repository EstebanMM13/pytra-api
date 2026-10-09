package com.estebanmm13.pytra_api.steamsync.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class SteamWebApiClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    @Value("${steam.api-key}")
    private String apiKey;

    public String getPersonaName(String steamId64) {
        String json = restClient.get()
                .uri("https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?key={key}&steamids={steamId64}",
                        apiKey, steamId64)
                .retrieve()
                .body(String.class);

        JsonNode players = parse(json).path("response").path("players");
        if (!players.isArray() || players.isEmpty()) return null;
        return players.get(0).path("personaname").asText(null);
    }

    public List<SteamOwnedGame> getOwnedGames(String steamId64) {
        String json = restClient.get()
                .uri("https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/?key={key}&steamid={steamId64}&include_appinfo=true&format=json",
                        apiKey, steamId64)
                .retrieve()
                .body(String.class);

        JsonNode games = parse(json).path("response").path("games");
        List<SteamOwnedGame> result = new ArrayList<>();
        if (!games.isArray()) return result;

        for (JsonNode game : games) {
            result.add(new SteamOwnedGame(
                    game.path("appid").asLong(),
                    game.path("name").asText("Unknown"),
                    game.path("playtime_forever").asLong(0)
            ));
        }
        return result;
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid response from Steam Web API", e);
        }
    }
}
