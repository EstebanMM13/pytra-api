package com.estebanmm13.pytra_api.games.service.genreService;

import com.estebanmm13.pytra_api.games.dto.genre.GenreResponseDto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GenreCreationResult {

    private GenreResponseDto genreResponseDto;
    private Boolean created;
}
