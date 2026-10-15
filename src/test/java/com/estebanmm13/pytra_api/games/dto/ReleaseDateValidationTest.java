package com.estebanmm13.pytra_api.games.dto;

import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Release dates may be in the future (unreleased games) up to ten years ahead; old dates are never rejected. */
class ReleaseDateValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsPastTodayFutureAndMissingDates() {
        assertThat(validator.validate(game(LocalDate.of(1998, 11, 21)))).isEmpty();
        assertThat(validator.validate(game(LocalDate.of(1900, 1, 1)))).isEmpty();
        assertThat(validator.validate(game(LocalDate.now()))).isEmpty();
        assertThat(validator.validate(game(LocalDate.now().plusMonths(8)))).isEmpty();
        assertThat(validator.validate(game(LocalDate.now().plusYears(10)))).isEmpty();
        assertThat(validator.validate(game(null))).isEmpty();
    }

    @Test
    void rejectsDatesMoreThanTenYearsAheadOnTheReleaseDateField() {
        assertThat(validator.validate(game(LocalDate.now().plusYears(10).plusDays(1))))
                .extracting(v -> v.getPropertyPath().toString())
                .containsOnly("releaseDate");
    }

    private static GameRequestDto game(LocalDate releaseDate) {
        GameRequestDto dto = new GameRequestDto();
        dto.setName("Game");
        dto.setCategory(GameCategory.SINGLEPLAYER);
        dto.setReleaseDate(releaseDate);
        return dto;
    }
}
