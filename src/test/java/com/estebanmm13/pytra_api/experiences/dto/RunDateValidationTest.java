package com.estebanmm13.pytra_api.experiences.dto;

import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceRequestDto;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Run dates and year go from 1970-01-01 to tomorrow (server date); the end date is never before the start. */
class RunDateValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsDatesInRangeAndMissingDates() {
        LocalDate today = LocalDate.now();
        assertThat(validator.validate(run(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1), 1970))).isEmpty();
        assertThat(validator.validate(run(today, today.plusDays(1), today.plusDays(1).getYear()))).isEmpty();
        assertThat(validator.validate(run(null, null, null))).isEmpty();
        assertThat(validator.validate(run(null, LocalDate.of(2020, 5, 1), null))).isEmpty();
        assertThat(validator.validate(run(LocalDate.of(2020, 5, 1), null, null))).isEmpty();
    }

    @Test
    void rejectsDatesBefore1970OnTheirField() {
        assertThat(validator.validate(run(LocalDate.of(1969, 12, 31), null, null)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsOnly("startDate");
        assertThat(validator.validate(run(null, LocalDate.of(1969, 12, 31), null)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsOnly("endDate");
    }

    @Test
    void rejectsDatesAfterTomorrowOnTheirField() {
        LocalDate dayAfterTomorrow = LocalDate.now().plusDays(2);
        assertThat(validator.validate(run(dayAfterTomorrow, null, null)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsOnly("startDate");
        assertThat(validator.validate(run(null, LocalDate.of(9999, 1, 1), null)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsOnly("endDate");
    }

    @Test
    void rejectsYearsOutOfRange() {
        assertThat(validator.validate(run(null, null, 1969)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsOnly("year");
        assertThat(validator.validate(run(null, null, LocalDate.now().plusDays(1).getYear() + 1)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsOnly("year");
    }

    @Test
    void rejectsEndBeforeStartOnTheEndDateField() {
        assertThat(validator.validate(run(LocalDate.of(2024, 3, 10), LocalDate.of(2024, 3, 9), null)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsOnly("endDate");
        assertThat(validator.validate(run(LocalDate.of(2024, 3, 10), LocalDate.of(2024, 3, 10), null))).isEmpty();
    }

    private static ExperienceRequestDto run(LocalDate startDate, LocalDate endDate, Integer year) {
        ExperienceRequestDto dto = new ExperienceRequestDto();
        dto.setRunLabel("Run");
        dto.setStatus(ExperienceStatus.COMPLETADO);
        dto.setPlatform("PC");
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        dto.setYear(year);
        return dto;
    }
}
