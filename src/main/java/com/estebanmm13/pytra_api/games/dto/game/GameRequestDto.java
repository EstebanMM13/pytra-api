package com.estebanmm13.pytra_api.games.dto.game;

import com.estebanmm13.pytra_api.games.model.GameCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
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

    @PastOrPresent
    private LocalDate releaseDate;

    @NotNull
    private GameCategory category;

    private Long sagaId;

    private String coverImageUrl;

    private Set<Long> genreIds = new HashSet<>();
}
