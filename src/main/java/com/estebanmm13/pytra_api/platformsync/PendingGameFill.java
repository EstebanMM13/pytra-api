package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;

import java.util.HashSet;
import java.util.List;

/**
 * Copies IGDB metadata onto a pending game, field by field, ONLY where the game has no value yet:
 * whatever the import or the user already set is never overwritten. Pure (mutates the given entity).
 */
public final class PendingGameFill {

    /** games.cover_image_url is VARCHAR(500). */
    private static final int MAX_COVER_URL_LENGTH = 500;

    private PendingGameFill() {
    }

    /**
     * @param category suggested category, may be null
     * @param genres   existing genres matching IGDB's, may be empty
     * @return whether any field was filled
     */
    public static boolean fillEmptyFields(Game game, IgdbGameMetadataDto metadata, GameCategory category,
                                          List<Genre> genres) {
        boolean changed = false;
        if (isBlank(game.getDeveloper()) && metadata.developer() != null) {
            game.setDeveloper(metadata.developer());
            changed = true;
        }
        if (isBlank(game.getPublisher()) && metadata.publisher() != null) {
            game.setPublisher(metadata.publisher());
            changed = true;
        }
        if (game.getReleaseDate() == null && metadata.releaseDate() != null) {
            game.setReleaseDate(metadata.releaseDate());
            changed = true;
        }
        if (game.getCategory() == null && category != null) {
            game.setCategory(category);
            changed = true;
        }
        String cover = metadata.coverImageUrl();
        if (isBlank(game.getCoverImageUrl()) && cover != null && cover.length() <= MAX_COVER_URL_LENGTH) {
            game.setCoverImageUrl(cover);
            changed = true;
        }
        if (game.getGenres().isEmpty() && !genres.isEmpty()) {
            game.setGenres(new HashSet<>(genres));
            changed = true;
        }
        return changed;
    }

    /** Whether the game has at least one field this fill could set. */
    public static boolean hasEmptyFields(Game game) {
        return isBlank(game.getDeveloper()) || isBlank(game.getPublisher()) || game.getReleaseDate() == null
                || game.getCategory() == null || isBlank(game.getCoverImageUrl()) || game.getGenres().isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
