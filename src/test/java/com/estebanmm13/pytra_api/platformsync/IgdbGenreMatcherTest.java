package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.Genre;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IgdbGenreMatcherTest {

    private final Genre rol = genre(1, "ROL");
    private final Genre aventura = genre(2, "AVENTURA");
    private final Genre shooter = genre(3, "SHOOTER");
    private final Genre estrategia = genre(4, "ESTRATEGIA");
    private final Genre simulacion = genre(5, "SIMULACIÓN");

    private final List<Genre> genres = List.of(rol, aventura, shooter, estrategia, simulacion);

    @Test
    void matchesByNameIgnoringCaseAndThenByAlias() {
        assertThat(IgdbGenreMatcher.match(List.of("Shooter", "Role-playing (RPG)", "Adventure"), genres))
                .containsExactly(shooter, rol, aventura);
    }

    @Test
    void accentsAreIgnored() {
        assertThat(IgdbGenreMatcher.match(List.of("Simulator"), genres)).containsExactly(simulacion);
    }

    @Test
    void twoIgdbGenresMatchingTheSameGenreAddItOnce() {
        assertThat(IgdbGenreMatcher.match(
                List.of("Turn-based strategy (TBS)", "Real Time Strategy (RTS)", "Strategy"), genres))
                .containsExactly(estrategia);
    }

    @Test
    void unmatchedGenresAreDroppedNeverCreated() {
        assertThat(IgdbGenreMatcher.match(List.of("Indie", "Arcade"), genres)).isEmpty();
        assertThat(IgdbGenreMatcher.match(List.of("Shooter"), List.of())).isEmpty();
    }

    private static Genre genre(long id, String name) {
        return Genre.builder().id(id).name(name).build();
    }
}
