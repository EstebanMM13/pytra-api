package com.estebanmm13.pytra_api.games.dto.game;

import com.estebanmm13.pytra_api.games.model.GameCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class GameRequestDto {

    @NotBlank
    private String name;

    private String developer;

    private String publisher;

    /** May be in the future (announced, unreleased games); see {@link #isReleaseDateInRange()}. */
    private LocalDate releaseDate;

    @NotNull
    private GameCategory category;

    private Long sagaId;

    private String coverImageUrl;

    private Set<Long> genreIds = new HashSet<>();

    /** Earliest accepted release year (video games predate it by little). */
    static final int MIN_RELEASE_YEAR = 1950;
    /** How many years ahead an announced release date may be. */
    static final int MAX_YEARS_AHEAD = 10;

    /** Sanity bounds now that future dates are allowed: 1950-01-01 up to ten years from today. */
    @AssertTrue(message = "must be between 1950 and ten years from now")
    public boolean isReleaseDateInRange() {
        if (releaseDate == null) {
            return true;
        }
        return releaseDate.getYear() >= MIN_RELEASE_YEAR
                && !releaseDate.isAfter(LocalDate.now().plusYears(MAX_YEARS_AHEAD));
    }
}
