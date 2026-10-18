package com.estebanmm13.pytra_api.platformsync;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Name handling shared by the platform importers when they match or create games. */
public final class ImportedGameNames {

    /** games.name is VARCHAR(255). */
    static final int MAX_NAME_LENGTH = 255;

    /** Trademark marks store names carry (PSN: "Marvel's Spider-Man™") that users never type. */
    private static final Pattern TRADEMARKS = Pattern.compile("[™®©]");
    /** Typographic apostrophes, primes, acute accents and backticks, matched as a plain apostrophe. */
    private static final Pattern APOSTROPHES = Pattern.compile("[‘’‚‛′´`]");
    /** Typographic double quotes and double primes, matched as a plain double quote. */
    private static final Pattern QUOTES = Pattern.compile("[“”„‟″]");
    /**
     * Console suffix PSN appends to some titles: "(PlayStation5)", "(PS4)", "(PS4 & PS5)" and a bare
     * trailing "PS4 & PS5". Applied after the trademark marks are gone ("PlayStation®5").
     */
    private static final Pattern PLATFORM_SUFFIX = Pattern.compile(
            "\\s*(?:\\((?:PlayStation\\s*[45]|PS[45](?:\\s*(?:&|and|/|\\+)\\s*PS[45])?)\\)"
                    + "|\\s-?\\s*PS4\\s*(?:&|and|/|\\+)\\s*PS5)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private ImportedGameNames() {
    }

    /** Stripped name that fits games.name; "Unknown" when the platform sent none. */
    public static String truncate(String name) {
        String safe = (name == null || name.isBlank()) ? "Unknown" : name.strip();
        return safe.length() <= MAX_NAME_LENGTH ? safe : safe.substring(0, MAX_NAME_LENGTH);
    }

    /**
     * Name with the store noise removed, case kept: trademark marks dropped, NFKC-normalized,
     * typographic apostrophes and quotes as ASCII, console suffix dropped and spaces collapsed.
     * "Grand Theft Auto V (PlayStation®5)" becomes "Grand Theft Auto V". Used as a search term.
     */
    public static String cleanName(String name) {
        // Marks first: NFKC would turn "™" into the letters "TM".
        String plain = TRADEMARKS.matcher(name).replaceAll(" ");
        plain = Normalizer.normalize(plain, Normalizer.Form.NFKC);
        plain = APOSTROPHES.matcher(plain).replaceAll("'");
        plain = QUOTES.matcher(plain).replaceAll("\"");
        plain = SPACES.matcher(plain).replaceAll(" ").strip();
        return PLATFORM_SUFFIX.matcher(plain).replaceAll("").strip();
    }

    /**
     * Key two names match on: {@link #cleanName(String) the clean name}, lower case. So
     * "Marvel’s Spider-Man™" finds a game typed as "Marvel's Spider-Man". Every step ignores case, so
     * names equal under the unique index on (user_id, LOWER(name)) always share a key and a miss never
     * collides with that index. Only compared in memory, never persisted.
     */
    public static String nameKey(String name) {
        return cleanName(name).toLowerCase(Locale.ROOT);
    }
}
