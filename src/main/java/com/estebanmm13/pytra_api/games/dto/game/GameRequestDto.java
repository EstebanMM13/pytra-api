package com.estebanmm13.pytra_api.games.dto.game;

import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.validation.ReleaseDateLimit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    /** May be in the future (announced, unreleased games), up to ten years ahead. */
    @ReleaseDateLimit
    private LocalDate releaseDate;

    @NotNull
    private GameCategory category;

    private Long sagaId;

    private String coverImageUrl;

    private Set<Long> genreIds = new HashSet<>();
}
