package com.estebanmm13.pytra_api.experiences.dto;

import com.estebanmm13.pytra_api.experiences.dto.experience.ExperienceRequestDto;
import com.estebanmm13.pytra_api.experiences.dto.onlinePlaytime.OnlinePlaytimeRequestDto;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.Platform;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Ratings are 0..10 with at most 2 decimals; anything else is a validation error, never rounded. */
class RatingValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.01", "8.5", "9.25", "9.50", "10", "10.00"})
    void acceptsRatingsFromZeroToTenWithUpToTwoDecimals(String rating) {
        assertThat(validator.validate(experience(rating))).isEmpty();
        assertThat(validator.validate(online(rating))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"9.255", "10.01", "-0.5", "-0.01", "11", "100"})
    void rejectsOutOfRangeOrMoreThanTwoDecimals(String rating) {
        assertThat(validator.validate(experience(rating)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsOnly("rating");
        assertThat(validator.validate(online(rating)))
                .extracting(v -> v.getPropertyPath().toString())
                .containsOnly("generalRating");
    }

    private static ExperienceRequestDto experience(String rating) {
        ExperienceRequestDto dto = new ExperienceRequestDto();
        dto.setRunLabel("Run");
        dto.setStatus(ExperienceStatus.COMPLETADO);
        dto.setPlatform(Platform.PC);
        dto.setRating(new BigDecimal(rating));
        return dto;
    }

    private static OnlinePlaytimeRequestDto online(String rating) {
        OnlinePlaytimeRequestDto dto = new OnlinePlaytimeRequestDto();
        dto.setTotalHours(1.0);
        dto.setGeneralRating(new BigDecimal(rating));
        return dto;
    }
}
