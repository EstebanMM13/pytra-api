package com.estebanmm13.pytra_api.platformsync;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Name handling shared by the platform importers when they match or create games. */
public final class ImportedGameNames {

    /** games.name is VARCHAR(255). */
    static final int MAX_NAME_LENGTH = 255;

    /** Trademark marks store names carry (PSN: "Marvel's Spider-Man™") that users never type. */
    private static final Pattern TRADEMARKS = Pattern.compile("[™®©]");
    /**
     * A mark followed by a space, punctuation or the end is simply dropped ("Tsushima™: Director's Cut"
     * must not become "Tsushima : ..."); one glued to the next word becomes a space ("PlayStation®5").
     */
    private static final Pattern TRADEMARK_BEFORE_BREAK =
            Pattern.compile("[™®©]+(?=[\\s\\p{Punct}‘’“”]|$)");
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
     * Name a pending placeholder game is stored with (and remembered as its imported name):
     * {@link #cleanName(String) the clean name}, so "Marvel's Spider-Man™ (PS5)" is saved as
     * "Marvel's Spider-Man", truncated to games.name. Falls back to the raw (truncated) name when cleaning
     * leaves nothing, so a title that is only marks or a console suffix is still saved under some name.
     *
     * <p>Importers must look the title up by {@code nameKey(placeholderName(raw))}: the key of the stored
     * value. Two names equal ignoring case always share that key, so a miss can never insert a duplicate
     * under the unique index on (user_id, LOWER(name)) unless the key is empty.
     */
    public static String placeholderName(String rawName) {
        String raw = truncate(rawName);
        String clean = cleanName(raw);
        return clean.isEmpty() ? raw : truncate(clean);
    }

    /**
     * Names to try, in order, for a game that must be told apart from another game of the user with the same
     * name (a different PSN concept): the name itself, then with the console as a suffix ("Until Dawn (PS5)"),
     * then numbered ("Until Dawn (PS5) (2)" ... up to {@code (maxNumber)}). Every candidate fits games.name.
     *
     * <p>The console suffix is the one {@link #cleanName} strips, so "Until Dawn (PS5)" keeps the name key
     * "until dawn": later syncs, the IGDB search and the IGDB match still see the plain name. The numbered
     * fallbacks (only when that name is taken too) lose that.
     */
    public static List<String> disambiguatedNames(String name, String consoleCode, int maxNumber) {
        String base = truncate(name);
        List<String> names = new ArrayList<>();
        names.add(base);
        String console = " (" + consoleCode + ")";
        names.add(withSuffix(base, console));
        for (int n = 2; n <= maxNumber; n++) {
            names.add(withSuffix(base, console + " (" + n + ")"));
        }
        return names;
    }

    /** {@code name + suffix}, cutting the name (never the suffix) so the result fits games.name. */
    private static String withSuffix(String name, String suffix) {
        int room = MAX_NAME_LENGTH - suffix.length();
        String head = name.length() <= room ? name : name.substring(0, room).strip();
        return head + suffix;
    }

    /**
     * Name with the store noise removed, case kept: trademark marks dropped, NFKC-normalized,
     * typographic apostrophes and quotes as ASCII, console suffix dropped and spaces collapsed.
     * "Grand Theft Auto V (PlayStation®5)" becomes "Grand Theft Auto V". Used as a search term.
     */
    public static String cleanName(String name) {
        // Marks first: NFKC would turn "™" into the letters "TM".
        String plain = TRADEMARK_BEFORE_BREAK.matcher(name).replaceAll("");
        plain = TRADEMARKS.matcher(plain).replaceAll(" ");
        plain = Normalizer.normalize(plain, Normalizer.Form.NFKC);
        plain = APOSTROPHES.matcher(plain).replaceAll("'");
        plain = QUOTES.matcher(plain).replaceAll("\"");
        plain = SPACES.matcher(plain).replaceAll(" ").strip();
        return PLATFORM_SUFFIX.matcher(plain).replaceAll("").strip();
    }

    /**
     * Key two names match on: {@link #cleanName(String) the clean name}, lower case, with standalone Roman
     * numerals II..XX as digits. So "Marvel’s Spider-Man™" finds a game typed as "Marvel's Spider-Man" and
     * "Hades II" finds "Hades 2". Every step ignores case, so names equal under the unique index on
     * (user_id, LOWER(name)) always share a key and a miss never collides with that index. Only compared
     * in memory, never persisted.
     */
    public static String nameKey(String name) {
        return romanToArabic(cleanName(name).toLowerCase(Locale.ROOT));
    }

    /**
     * Rewrites whole-token Roman numerals II..XX (lower case) as digits: "final fantasy vii: remake" becomes
     * "final fantasy 7: remake". A token is delimited by anything that is not a letter or digit, so "mix",
     * "civil" or "v2" are left alone. "i" and "x" are never converted: "I Am Bread" is not "1 Am Bread" and
     * "Mega Man X" is not "Mega Man 10".
     */
    static String romanToArabic(String lowerCaseName) {
        return ROMAN_TOKEN.matcher(lowerCaseName).replaceAll(match -> ROMAN_VALUES.get(match.group()));
    }

    private static final Map<String, String> ROMAN_VALUES = romanValues();
    private static final Pattern ROMAN_TOKEN = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?:" + String.join("|", ROMAN_VALUES.keySet()) + ")(?![\\p{L}\\p{N}])");

    /** Canonical numerals 2..20 except "x", longest first so the alternation never stops at a prefix. */
    private static Map<String, String> romanValues() {
        String[] numerals = {"ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii", "xiii", "xiv",
                "xv", "xvi", "xvii", "xviii", "xix", "xx"};
        Map<String, Integer> byNumeral = new HashMap<>();
        for (int i = 0; i < numerals.length; i++) {
            if (!numerals[i].equals("x")) byNumeral.put(numerals[i], i + 2);
        }
        Map<String, String> values = new LinkedHashMap<>();
        byNumeral.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .forEach(numeral -> values.put(numeral, String.valueOf(byNumeral.get(numeral))));
        return values;
    }
}
