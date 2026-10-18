package com.estebanmm13.pytra_api.metadata.client;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Apicalypse bodies for the IGDB {@code /games} endpoint. The only user input is the search term,
 * which always ends up inside a double-quoted string literal, escaped.
 */
final class IgdbQueries {

    /** Fields for a search row: kept small, results only need a thumbnail and a hint of which game it is. */
    private static final String SEARCH_FIELDS =
            "name,first_release_date,cover.image_id,platforms.name,platforms.abbreviation,game_type";

    private static final String DETAILS_FIELDS = "name,first_release_date,cover.image_id,artworks.image_id,"
            + "genres.name,involved_companies.company.name,involved_companies.developer,"
            + "involved_companies.publisher,platforms.name,game_type";

    /** Everything a details query reads, plus the game modes (ids) a category is suggested from. */
    private static final String CANDIDATE_FIELDS = DETAILS_FIELDS + ",game_modes";

    private IgdbQueries() {
    }

    /**
     * {@code version_parent = null} drops editions ("Deluxe Edition", "GOTY"...) that duplicate their
     * base game. Bundles, packs and mods are filtered afterwards by {@link IgdbParser} (filtering on
     * {@code game_type} in the query would also drop games whose type IGDB never filled in), so a few
     * more rows than needed are requested.
     */
    static String search(String term, int limit) {
        return "search \"" + escape(term) + "\"; fields " + SEARCH_FIELDS
                + "; where version_parent = null; limit " + limit + ";";
    }

    /**
     * Search restricted to games released on at least one of {@code platformIds}, returning the full
     * details of every hit so a match needs no second request. Same edition filter as {@link #search}.
     */
    static String searchOnPlatforms(String term, Collection<Integer> platformIds, int limit) {
        String platforms = platformIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        return "search \"" + escape(term) + "\"; fields " + CANDIDATE_FIELDS
                + "; where version_parent = null & platforms = (" + platforms + "); limit " + limit + ";";
    }

    static String details(long igdbId) {
        return "fields " + DETAILS_FIELDS + "; where id = " + igdbId + "; limit 1;";
    }

    /**
     * Makes {@code raw} safe inside an Apicalypse double-quoted string: backslashes and quotes are
     * escaped and control characters (newlines included) become spaces, so the term can never close
     * the literal and start another clause.
     */
    static String escape(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' || c == '"') {
                out.append('\\').append(c);
            } else if (Character.isISOControl(c)) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
