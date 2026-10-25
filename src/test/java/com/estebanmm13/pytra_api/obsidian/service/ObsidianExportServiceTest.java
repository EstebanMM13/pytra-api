package com.estebanmm13.pytra_api.obsidian.service;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.model.Saga;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.obsidian.dto.ObsidianExportDto;
import com.estebanmm13.pytra_api.obsidian.dto.ObsidianExportDto.ExperienceDto;
import com.estebanmm13.pytra_api.obsidian.dto.ObsidianExportDto.GameDto;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObsidianExportServiceTest {

    private static final Long USER = 7L;
    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00.123Z");
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 10, 1, 8, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 10, 20, 8, 0);

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final OnlinePlaytimeRepository onlineRepository = mock(OnlinePlaytimeRepository.class);
    private final ObsidianExportService service = new ObsidianExportService(
            gameRepository, experienceRepository, onlineRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    private final Platform ps5 = Platform.builder().id(1L).name("PS5").sortOrder(2).build();
    private final Platform pc = Platform.builder().id(2L).name("PC").sortOrder(1).build();
    private final Saga cod = Saga.builder().id(3L).userId(USER).name("Call of Duty").updatedAt(T1).build();

    @Test
    void exportsConfirmedGamesWithTheirSagasRunsAndOnlinePlaytime() {
        Game blackOps = Game.builder().id(12L).userId(USER).name("Call of Duty: Black Ops 7").saga(cod)
                .category(GameCategory.HYBRID).developer("Treyarch").publisher("Activision")
                .releaseDate(LocalDate.of(2025, 11, 14)).reviewStatus(ReviewStatus.CONFIRMED)
                .genres(Set.of(Genre.builder().id(1L).name("Shooter").build(),
                        Genre.builder().id(2L).name("Action").build()))
                .updatedAt(T1).build();
        Game solo = Game.builder().id(5L).userId(USER).name("Solo").reviewStatus(ReviewStatus.CONFIRMED)
                .updatedAt(T1).build();
        when(gameRepository.findAllByUserIdAndReviewStatus(USER, ReviewStatus.CONFIRMED))
                .thenReturn(List.of(blackOps, solo));

        // Online playtime and a run of a pending game (id 99, not returned above) must not leak into the export.
        when(onlineRepository.findAllByUserId(USER)).thenReturn(List.of(
                OnlinePlaytime.builder().id(1L).userId(USER).gameId(12L).totalHours(120.5)
                        .generalRating(new BigDecimal("8.50")).platforms(Set.of(ps5, pc)).platinum(true).updatedAt(T2).build(),
                OnlinePlaytime.builder().id(2L).userId(USER).gameId(99L).totalHours(3.0).updatedAt(T1).build()));
        when(experienceRepository.findAllByUserId(USER)).thenReturn(List.of(
                run(41L, 12L, "Run 2", LocalDate.of(2026, 1, 1), new BigDecimal("10.00")),
                run(40L, 12L, "Run 1", LocalDate.of(2025, 11, 14), new BigDecimal("9.25")),
                run(50L, 99L, "Pending run", null, null)));

        ObsidianExportDto export = service.export(USER);

        verify(gameRepository).findAllByUserIdAndReviewStatus(USER, ReviewStatus.CONFIRMED);
        assertThat(export.schemaVersion()).isEqualTo(1);
        assertThat(export.serverTime()).isEqualTo(Instant.parse("2026-10-24T10:00:00Z"));
        assertThat(export.sagas()).singleElement().satisfies(saga -> {
            assertThat(saga.id()).isEqualTo(3L);
            assertThat(saga.name()).isEqualTo("Call of Duty");
            assertThat(saga.updatedAt()).isEqualTo(instant(T1));
        });

        assertThat(export.games()).extracting(GameDto::id).containsExactly(5L, 12L);
        GameDto solo5 = export.games().get(0);
        assertThat(solo5.sagaId()).isNull();
        assertThat(solo5.category()).isNull();
        assertThat(solo5.online()).isNull();
        assertThat(solo5.genres()).isEmpty();

        GameDto game = export.games().get(1);
        assertThat(game.sagaId()).isEqualTo(3L);
        assertThat(game.category()).isEqualTo("HYBRID");
        assertThat(game.genres()).containsExactly("Action", "Shooter");
        assertThat(game.developer()).isEqualTo("Treyarch");
        assertThat(game.publisher()).isEqualTo("Activision");
        assertThat(game.releaseDate()).isEqualTo(LocalDate.of(2025, 11, 14));
        assertThat(game.online().totalHours()).isEqualTo(120.5);
        assertThat(game.online().platinum()).isTrue();
        assertThat(game.online().fullCompletion()).isFalse();
        assertThat(game.online().platforms()).containsExactly("PC", "PS5");
        assertThat(game.online().rating()).isEqualByComparingTo("8.5");
        assertThat(game.online().rating().toPlainString()).isEqualTo("8.5");
        // The online playtime changed later than the game itself.
        assertThat(game.updatedAt()).isEqualTo(instant(T2));

        assertThat(export.experiences()).extracting(ExperienceDto::id).containsExactly(40L, 41L);
        ExperienceDto first = export.experiences().get(0);
        assertThat(first.gameId()).isEqualTo(12L);
        assertThat(first.runLabel()).isEqualTo("Run 1");
        assertThat(first.year()).isEqualTo(2025);
        assertThat(first.status()).isEqualTo("COMPLETADO");
        assertThat(first.rating().toPlainString()).isEqualTo("9.25");
        assertThat(first.hours()).isEqualTo(14.5);
        assertThat(first.platform()).isEqualTo("PS5");
        assertThat(first.platinum()).isTrue();
        assertThat(first.fullCompletion()).isTrue();
        assertThat(first.replay()).isFalse();
        assertThat(export.experiences().get(1).rating().toPlainString()).isEqualTo("10");
    }

    @Test
    void emptyLibraryExportsEmptyLists() {
        ObsidianExportDto export = service.export(USER);

        assertThat(export.sagas()).isEmpty();
        assertThat(export.games()).isEmpty();
        assertThat(export.experiences()).isEmpty();
    }

    private Experience run(Long id, Long gameId, String label, LocalDate startDate, BigDecimal rating) {
        return Experience.builder().id(id).userId(USER).gameId(gameId).runLabel(label)
                .status(ExperienceStatus.COMPLETADO).rating(rating).hours(14.5)
                .startDate(startDate).endDate(startDate != null ? startDate.plusDays(6) : null)
                .platform(ps5).platinum(true).fullCompletion(true).replay(false).updatedAt(T2).build();
    }

    private static Instant instant(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
