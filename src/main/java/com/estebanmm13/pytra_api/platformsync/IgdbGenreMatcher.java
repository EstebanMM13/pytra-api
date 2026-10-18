package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.Genre;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Maps IGDB genre names onto existing genres, the same way the web's manual autofill does
 * ({@code matchGenres} in pytra-web's metadata-autofill.logic.ts, IGDB aliases only): case-, accent-
 * and space-insensitive, then well-known aliases. Unmatched IGDB genres are dropped; genres are never
 * created here (the manual flow only creates one when the user clicks its suggestion). Pure.
 */
public final class IgdbGenreMatcher {

    private static final Pattern MARKS = Pattern.compile("\\p{M}");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Locale SPANISH = Locale.forLanguageTag("es");

    /** Keep in sync with GENRE_ALIASES (IGDB part) in pytra-web. Keys and values are {@link #genreKey} keys. */
    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("ROLE-PLAYING (RPG)", List.of("ROL", "RPG", "ROLE-PLAYING", "ROLE PLAYING")),
            Map.entry("ADVENTURE", List.of("AVENTURA")),
            Map.entry("PLATFORM", List.of("PLATAFORMAS", "PLATAFORMA", "PLATFORMER", "PLATFORMS")),
            Map.entry("SHOOTER", List.of("DISPAROS")),
            Map.entry("PUZZLE", List.of("PUZLES", "PUZLE", "PUZZLES", "ROMPECABEZAS")),
            Map.entry("RACING", List.of("CARRERAS", "CONDUCCION")),
            Map.entry("SPORT", List.of("DEPORTES", "DEPORTE", "SPORTS")),
            Map.entry("STRATEGY", List.of("ESTRATEGIA")),
            Map.entry("FIGHTING", List.of("LUCHA", "PELEA", "PELEAS")),
            Map.entry("HACK AND SLASH/BEAT 'EM UP", List.of("HACK AND SLASH", "BEAT 'EM UP", "HACK 'N' SLASH", "BEAT EM UP")),
            Map.entry("SIMULATOR", List.of("SIMULACION", "SIMULADORES", "SIMULADOR", "SIMULATION")),
            Map.entry("POINT-AND-CLICK", List.of("AVENTURA GRAFICA", "POINT AND CLICK")),
            Map.entry("MUSIC", List.of("MUSICA", "MUSICAL", "RITMO")),
            Map.entry("TURN-BASED STRATEGY (TBS)", List.of("ESTRATEGIA POR TURNOS", "TURNOS", "TBS", "ESTRATEGIA")),
            Map.entry("REAL TIME STRATEGY (RTS)", List.of("ESTRATEGIA EN TIEMPO REAL", "RTS", "ESTRATEGIA")),
            Map.entry("TACTICAL", List.of("TACTICO", "TACTICA", "TACTICOS")),
            Map.entry("VISUAL NOVEL", List.of("NOVELA VISUAL")),
            Map.entry("CARD & BOARD GAME", List.of("CARTAS", "JUEGOS DE CARTAS", "JUEGOS DE MESA", "CARD GAME")),
            Map.entry("QUIZ/TRIVIA", List.of("PREGUNTAS", "TRIVIAL", "TRIVIA"))
    );

    private IgdbGenreMatcher() {
    }

    /** Existing genres matching some IGDB genre, each at most once, in IGDB's order. */
    public static List<Genre> match(List<String> igdbGenres, List<Genre> existingGenres) {
        Map<String, Genre> byKey = new HashMap<>();
        for (Genre genre : existingGenres) {
            byKey.putIfAbsent(genreKey(genre.getName()), genre);
        }

        List<Genre> matched = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String igdbGenre : igdbGenres) {
            String key = genreKey(igdbGenre);
            if (key.isEmpty() || !seen.add(key)) continue;
            List<String> candidates = new ArrayList<>();
            candidates.add(key);
            candidates.addAll(ALIASES.getOrDefault(key, List.of()));
            candidates.stream().map(byKey::get).filter(g -> g != null).findFirst()
                    .filter(g -> !matched.contains(g))
                    .ifPresent(matched::add);
        }
        return matched;
    }

    /** Trimmed, single-spaced, upper case and without accents ("Acción" and "ACCION" share a key). */
    static String genreKey(String name) {
        if (name == null) return "";
        String plain = MARKS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(plain.strip()).replaceAll(" ").toUpperCase(SPANISH);
    }
}
