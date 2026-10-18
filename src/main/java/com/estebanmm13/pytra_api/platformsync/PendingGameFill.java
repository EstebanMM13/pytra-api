package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.metadata.dto.IgdbGameMetadataDto;

import java.util.HashSet;
import java.util.List;

/**
 * Copies a confident IGDB match onto a pending game without ever overwriting user data. Pure (mutates
 * the given entity).
 *
 * <ul>
 *   <li>Developer, publisher, release date, category and genres: filled only when empty.</li>
 *   <li>Cover: filled when empty, or replaced when it is still the platform image the import set
 *       ({@link Game#getImportedCoverUrl()}); a cover the user set is kept.</li>
 *   <li>Name: see {@link #importedNameToReplace}; the caller checks the unique name index first.</li>
 * </ul>
 */
public final class PendingGameFill {

    /** games.cover_image_url is VARCHAR(500). */
    private static final int MAX_COVER_URL_LENGTH = 500;

    private PendingGameFill() {
    }

    /**
     * @param category suggested category, may be null
     * @param genres   existing genres matching IGDB's, may be empty
     * @return whether any field was set
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
        if (cover != null && cover.length() <= MAX_COVER_URL_LENGTH && !cover.equals(game.getCoverImageUrl())
                && (isBlank(game.getCoverImageUrl()) || coverIsStillImported(game))) {
            game.setCoverImageUrl(cover);
            changed = true;
        }
        if (game.getGenres().isEmpty() && !genres.isEmpty()) {
            game.setGenres(new HashSet<>(genres));
            changed = true;
        }
        return changed;
    }

    /**
     * IGDB's canonical name when it may replace the game's name: the game still has exactly the name the
     * import stored (the user never edited it) and IGDB's differs. Null otherwise.
     */
    public static String importedNameToReplace(Game game, IgdbGameMetadataDto metadata) {
        String igdbName = metadata.name();
        if (igdbName == null || game.getImportedName() == null) return null;
        if (!game.getImportedName().equals(game.getName()) || igdbName.equals(game.getName())) return null;
        return igdbName;
    }

    private static boolean coverIsStillImported(Game game) {
        return game.getImportedCoverUrl() != null && game.getImportedCoverUrl().equals(game.getCoverImageUrl());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
