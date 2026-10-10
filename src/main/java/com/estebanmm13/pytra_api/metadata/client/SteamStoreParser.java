package com.estebanmm13.pytra_api.metadata.client;

import com.estebanmm13.pytra_api.error.SteamIntegrationException;
import com.estebanmm13.pytra_api.metadata.dto.SteamGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.SteamStoreSearchResultDto;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns Steam store JSON ({@code storesearch} and {@code appdetails}) into our DTOs. Pure and
 * defensive: the store API is undocumented, so every field may be missing or of an odd type.
 */
public final class SteamStoreParser {

    /** Longest values the games table accepts (VARCHAR(255) / cover VARCHAR(500)). */
    private static final int MAX_TEXT_LENGTH = 255;
    private static final int MAX_URL_LENGTH = 500;

    private SteamStoreParser() {
    }

    /** Store search hits, skipping bundles/packages (only {@code type == "app"} has an appid) and malformed items. */
    public static List<SteamStoreSearchResultDto> parseSearch(JsonNode root, int limit) {
        List<SteamStoreSearchResultDto> results = new ArrayList<>();
        JsonNode items = root.path("items");
        if (!items.isArray()) return results;

        for (JsonNode item : items) {
            if (results.size() >= limit) break;
            String type = item.path("type").asText("app");
            long appId = item.path("id").asLong(0);
            String name = text(item.path("name"));
            if (!"app".equals(type) || appId <= 0 || name == null) continue;
            results.add(new SteamStoreSearchResultDto(appId, name, httpsUrl(item.path("tiny_image"))));
        }
        return results;
    }

    /**
     * Normalizes an {@code appdetails} response.
     *
     * @throws SteamIntegrationException APP_NOT_FOUND when Steam answers {@code success:false} (unknown
     *                                   or region-locked app), APP_NOT_A_GAME for DLC, soundtracks, demos...
     */
    public static SteamGameMetadataDto parseDetails(JsonNode root, long appId) {
        JsonNode entry = root.path(String.valueOf(appId));
        JsonNode data = entry.path("data");
        if (!entry.path("success").asBoolean(false) || !data.isObject()) {
            throw SteamIntegrationException.appNotFound();
        }
        if (!"game".equals(data.path("type").asText(""))) {
            throw SteamIntegrationException.appNotAGame();
        }

        String name = text(data.path("name"));
        if (name == null) throw SteamIntegrationException.appNotFound();

        return new SteamGameMetadataDto(
                appId,
                name,
                firstText(data.path("developers")),
                firstText(data.path("publishers")),
                releaseDate(data.path("release_date")),
                genres(data.path("genres")),
                httpsUrl(data.path("header_image"))
        );
    }

    private static LocalDate releaseDate(JsonNode releaseDate) {
        // Unreleased games often carry a target date; it is not a release date (and the game form rejects future dates).
        if (releaseDate.path("coming_soon").asBoolean(false)) return null;
        LocalDate parsed = SteamReleaseDateParser.parse(releaseDate.path("date").asText(null));
        return parsed != null && parsed.isAfter(LocalDate.now()) ? null : parsed;
    }

    private static List<String> genres(JsonNode genres) {
        List<String> names = new ArrayList<>();
        if (!genres.isArray()) return names;
        for (JsonNode genre : genres) {
            String name = text(genre.path("description"));
            if (name != null && !names.contains(name)) names.add(name);
        }
        return names;
    }

    private static String firstText(JsonNode array) {
        if (!array.isArray()) return null;
        for (JsonNode value : array) {
            String text = text(value);
            if (text != null) return text;
        }
        return null;
    }

    private static String text(JsonNode node) {
        if (!node.isTextual()) return null;
        String value = node.asText().strip();
        if (value.isEmpty() || value.length() > MAX_TEXT_LENGTH) return null;
        return value;
    }

    /** Store images are served over https; anything else (or absurdly long) is dropped rather than trusted. */
    private static String httpsUrl(JsonNode node) {
        if (!node.isTextual()) return null;
        String url = node.asText().strip();
        if (url.startsWith("http://")) url = "https://" + url.substring("http://".length());
        if (!url.startsWith("https://") || url.length() > MAX_URL_LENGTH) return null;
        return url;
    }
}
