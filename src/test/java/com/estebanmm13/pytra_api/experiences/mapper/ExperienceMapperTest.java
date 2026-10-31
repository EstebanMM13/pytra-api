package com.estebanmm13.pytra_api.experiences.mapper;

import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceResponseDto;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceMapperTest {

    private final ExperienceMapper mapper = new ExperienceMapper();

    @Test
    void mapsThePostgameFields() {
        Experience run = Experience.builder().id(1L).status(ExperienceStatus.COMPLETADO).postgame(true).build();
        Experience ended = Experience.builder().id(2L).status(ExperienceStatus.COMPLETADO)
                .postgameEndDate(LocalDate.of(2026, 10, 1)).build();

        ExperienceResponseDto playing = mapper.toResponseDto(run);
        ExperienceResponseDto finished = mapper.toResponseDto(ended);

        assertThat(playing.getPostgame()).isTrue();
        assertThat(playing.getPostgameEndDate()).isNull();
        assertThat(finished.getPostgame()).isFalse();
        assertThat(finished.getPostgameEndDate()).isEqualTo(LocalDate.of(2026, 10, 1));
    }
}
