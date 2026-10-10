package com.estebanmm13.pytra_api.metadata.client;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the localized {@code release_date.date} string of the Steam store.
 *
 * <p>The format depends on the store language and has changed over time: "24 FEB 2022" and
 * "24 feb. 2022" (Spanish), "24 Feb, 2022" and "Feb 24, 2022" (English), "24 de febrero de 2022".
 * Rather than one formatter per variant, the string is split into tokens and a day, a month name
 * (Spanish or English, matched by its first three letters) and a year are picked out. Anything
 * without all three ("Q1 2025", "2025", "Próximamente"...) yields null.
 */
final class SteamReleaseDateParser {

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("ene", 1), Map.entry("jan", 1),
            Map.entry("feb", 2),
            Map.entry("mar", 3),
            Map.entry("abr", 4), Map.entry("apr", 4),
            Map.entry("may", 5),
            Map.entry("jun", 6),
            Map.entry("jul", 7),
            Map.entry("ago", 8), Map.entry("aug", 8),
            Map.entry("sep", 9),
            Map.entry("oct", 10),
            Map.entry("nov", 11),
            Map.entry("dic", 12), Map.entry("dec", 12)
    );

    private SteamReleaseDateParser() {
    }

    static LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) return null;

        String cleaned = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[.,]", " ");
        List<String> tokens = new ArrayList<>();
        for (String token : cleaned.trim().split("\\s+")) {
            if (!token.equals("de")) tokens.add(token);
        }
        if (tokens.size() != 3) return null;

        Integer day = null;
        Integer month = null;
        Integer year = null;
        for (String token : tokens) {
            if (token.matches("\\d{1,2}") && day == null) {
                day = Integer.parseInt(token);
            } else if (token.matches("\\d{4}") && year == null) {
                year = Integer.parseInt(token);
            } else if (token.matches("\\p{L}{3,}") && month == null) {
                month = MONTHS.get(token.substring(0, 3));
                if (month == null) return null;
            } else {
                return null;
            }
        }
        if (day == null || month == null || year == null) return null;

        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return null;
        }
    }
}
