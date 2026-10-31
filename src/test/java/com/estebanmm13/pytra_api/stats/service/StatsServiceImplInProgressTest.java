package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import com.estebanmm13.pytra_api.stats.dto.InProgressExperienceDto;
import com.estebanmm13.pytra_api.stats.repository.YearNoteRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** "Playing now": runs in progress plus completed runs whose postgame the user is still playing. */
class StatsServiceImplInProgressTest {

    private static final Long USER_ID = 7L;

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final ExperienceRepository experienceRepository = mock(ExperienceRepository.class);
    private final StatsServiceImpl service = new StatsServiceImpl(gameRepository, mock(SagaRepository.class),
            experienceRepository, mock(OnlinePlaytimeRepository.class), mock(YearNoteRepository.class),
            mock(DiaryEntryGameRepository.class));

    @Test
    void postgameRunsArePlayedNowAndCarryTheirFlag() {
        Experience playing = run(1L, ExperienceStatus.EN_CURSO, null, LocalDate.of(2026, 9, 1));
        Experience postgame = run(2L, ExperienceStatus.COMPLETADO, true, LocalDate.of(2026, 10, 1));
        Experience finished = run(3L, ExperienceStatus.COMPLETADO, false, LocalDate.of(2026, 10, 5));
        Experience postgameEnded = run(4L, ExperienceStatus.COMPLETADO, null, LocalDate.of(2026, 10, 6));
        when(experienceRepository.findAllByUserIdAndStatusIn(eq(USER_ID), any()))
                .thenReturn(List.of(playing, postgame, finished, postgameEnded));

        List<InProgressExperienceDto> result = service.getInProgress(USER_ID);

        assertThat(result).extracting(InProgressExperienceDto::getExperienceId).containsExactly(2L, 1L);
        assertThat(result).extracting(InProgressExperienceDto::getPostgame).containsExactly(true, false);
    }

    private static Experience run(Long id, ExperienceStatus status, Boolean postgame, LocalDate startDate) {
        return Experience.builder().id(id).userId(USER_ID).gameId(9L).runLabel("Run " + id).status(status)
                .postgame(postgame).startDate(startDate).hours(1.0)
                .platform(Platform.builder().name("PS5").legacyCode("PS5").build())
                .build();
    }
}
