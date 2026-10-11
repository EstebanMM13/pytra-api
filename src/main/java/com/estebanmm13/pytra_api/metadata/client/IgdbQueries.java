package com.estebanmm13.pytra_api.metadata.client;

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
