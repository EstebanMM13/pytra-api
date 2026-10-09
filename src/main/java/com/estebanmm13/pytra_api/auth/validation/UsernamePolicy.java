package com.estebanmm13.pytra_api.auth.validation;

import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Single source of truth for username rules. Usernames are stored lowercase and may never contain '@',
 * so a login identifier is unambiguous: with '@' it is an email, without it a username.
 */
public final class UsernamePolicy {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;

    /** Checked against the trimmed input; letters may be uppercase because the display form keeps case. */
    public static final String INPUT_REGEX = "^[A-Za-z0-9._-]{3,30}$";
    /** Form of the stored (normalized) username. */
    public static final Pattern NORMALIZED_PATTERN = Pattern.compile("^[a-z0-9._-]{3,30}$");

    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-z0-9._-]");
    private static final String FALLBACK_BASE = "user";
    /** Leaves room for a numeric suffix when the generated base is already taken. */
    private static final int GENERATED_BASE_MAX_LENGTH = MAX_LENGTH - 5;

    private UsernamePolicy() {
    }

    /** Trimmed, locale-independent lowercase form used for storage, lookups and uniqueness. */
    public static String normalize(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    /** Trimmed, locale-independent lowercase form of an email. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String username) {
        return username != null && NORMALIZED_PATTERN.matcher(normalize(username)).matches();
    }

    /**
     * Builds a valid, unused username from an email's local part (Google sign-up): invalid characters are
     * stripped, short results are padded, long ones truncated, and a numeric suffix resolves collisions.
     */
    public static String generateFromEmail(String email, Predicate<String> isTaken) {
        String localPart = normalizeEmail(email == null ? "" : email).split("@", 2)[0];
        String base = INVALID_CHARS.matcher(localPart).replaceAll("");

        if (base.isEmpty()) {
            base = FALLBACK_BASE;
        }
        if (base.length() > GENERATED_BASE_MAX_LENGTH) {
            base = base.substring(0, GENERATED_BASE_MAX_LENGTH);
        }
        StringBuilder padded = new StringBuilder(base);
        while (padded.length() < MIN_LENGTH) {
            padded.append('0');
        }
        base = padded.toString();

        String candidate = base;
        int suffix = 1;
        while (isTaken.test(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }
}
