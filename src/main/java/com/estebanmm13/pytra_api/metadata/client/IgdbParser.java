package com.estebanmm13.pytra_api.metadata.client;

import com.estebanmm13.pytra_api.error.IgdbIntegrationException;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;
import com.estebanmm13.pytra_api.metadata.dto.IgdbSearchResultDto;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Turns IGDB {@code /games} JSON arrays into our DTOs. Pure and defensive: any field may be missing.
 *
 * <p>Images: Pytra shows covers as wide banners (Steam header ratio, ~2.14:1, {@code object-cover}).
 * IGDB's cover is portrait box art, which a banner crop reduces to a thin middle strip, so the game
 * cover prefers the official portrait cover ({@code t_cover_big}): IGDB artworks are unordered and
 * untyped (the first one can be a publisher logo), so they are only a fallback ({@code t_screenshot_big}). Search rows show the small portrait cover.
 */
public final class IgdbParser {

    static final String IMAGE_BASE_URL = "https://images.igdb.com/igdb/image/upload/";

    /**
     * {@code game_type} ids that are never something a person "plays" as a game of its own:
     * bundle (3), mod (5), fork (12), pack (13), update (14). DLC (1), expansions (2, 4), episodes,
     * seasons, remakes, remasters and ports stay: people log "God of War Ragnarök: Valhalla" as a game.
     */
    private static final Set<Integer> EXCLUDED_GAME_TYPES = Set.of(3, 5, 12, 13, 14);

    /** IGDB image ids are short lowercase alphanumerics; anything else is not put into a URL. */
    private static final Pattern IMAGE_ID = Pattern.compile("[a-z0-9]{1,64}");

    /** Longest values the games table accepts (VARCHAR(255)). */
    private static final int MAX_TEXT_LENGTH = 255;

    private IgdbParser() {
    }

    public static List<IgdbSearchResultDto> parseSearch(JsonNode root, int limit) {
        List<IgdbSearchResultDto> results = new ArrayList<>();
        if (!root.isArray()) return results;

        for (JsonNode game : root) {
            if (results.size() >= limit) break;
            long id = game.path("id").asLong(0);
            String name = text(game.path("name"));
            if (id <= 0 || name == null || isExcluded(game)) continue;
            LocalDate firstRelease = firstReleaseDate(game);
            results.add(new IgdbSearchResultDto(
                    id,
                    name,
                    firstRelease == null ? null : firstRelease.getYear(),
                    imageUrl("t_cover_small", game.path("cover").path("image_id")),
                    names(game.path("platforms"), p -> {
                        String abbreviation = text(p.path("abbreviation"));
                        return abbreviation != null ? abbreviation : text(p.path("name"));
                    })
            ));
        }
        return results;
    }

    /**
     * Normalizes the single game of a details query.
     *
     * @throws IgdbIntegrationException GAME_NOT_FOUND when IGDB returns no (usable) game for the id
     */
    public static IgdbGameMetadataDto parseDetails(JsonNode root, long igdbId) {
        JsonNode game = root.isArray() && !root.isEmpty() ? root.get(0) : null;
        if (game == null || game.path("id").asLong(0) != igdbId) throw IgdbIntegrationException.gameNotFound();
        String name = text(game.path("name"));
        if (name == null) throw IgdbIntegrationException.gameNotFound();

        LocalDate releaseDate = firstReleaseDate(game);
        // Announced games often carry a target date; the game form rejects future dates.
        if (releaseDate != null && releaseDate.isAfter(LocalDate.now(ZoneOffset.UTC))) releaseDate = null;

        String cover = imageUrl("t_cover_big", game.path("cover").path("image_id"));
        if (cover == null) cover = imageUrl("t_screenshot_big", firstImageId(game.path("artworks")));

        return new IgdbGameMetadataDto(
                igdbId,
                name,
                firstCompany(game.path("involved_companies"), "developer"),
                firstCompany(game.path("involved_companies"), "publisher"),
                releaseDate,
                names(game.path("genres"), g -> text(g.path("name"))),
                cover,
                names(game.path("platforms"), p -> text(p.path("name")))
        );
    }

    private static boolean isExcluded(JsonNode game) {
        JsonNode type = game.path("game_type");
        // Expanded ({"id": 3, "type": "Bundle"}) or plain id, depending on the requested fields.
        JsonNode id = type.isObject() ? type.path("id") : type;
        return id.isIntegralNumber() && EXCLUDED_GAME_TYPES.contains(id.asInt());
    }

    /** {@code first_release_date} is a unix timestamp in seconds. */
    private static LocalDate firstReleaseDate(JsonNode game) {
        JsonNode timestamp = game.path("first_release_date");
        if (!timestamp.isIntegralNumber()) return null;
        try {
            return LocalDate.ofInstant(Instant.ofEpochSecond(timestamp.asLong()), ZoneOffset.UTC);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String firstCompany(JsonNode involvedCompanies, String role) {
        if (!involvedCompanies.isArray()) return null;
        for (JsonNode involved : involvedCompanies) {
            if (!involved.path(role).asBoolean(false)) continue;
            String name = text(involved.path("company").path("name"));
            if (name != null) return name;
        }
        return null;
    }

    private static JsonNode firstImageId(JsonNode images) {
        if (!images.isArray()) return null;
        for (JsonNode image : images) {
            JsonNode id = image.path("image_id");
            if (id.isTextual() && IMAGE_ID.matcher(id.asText()).matches()) return id;
        }
        return null;
    }

    private static String imageUrl(String size, JsonNode imageId) {
        if (imageId == null || !imageId.isTextual() || !IMAGE_ID.matcher(imageId.asText()).matches()) return null;
        return IMAGE_BASE_URL + size + "/" + imageId.asText() + ".jpg";
    }

    private static List<String> names(JsonNode array, Function<JsonNode, String> name) {
        List<String> names = new ArrayList<>();
        if (!array.isArray()) return names;
        for (JsonNode item : array) {
            String value = name.apply(item);
            if (value != null && !names.contains(value)) names.add(value);
        }
        return names;
    }

    private static String text(JsonNode node) {
        if (!node.isTextual()) return null;
        String value = node.asText().strip();
        if (value.isEmpty() || value.length() > MAX_TEXT_LENGTH) return null;
        return value;
    }
}
