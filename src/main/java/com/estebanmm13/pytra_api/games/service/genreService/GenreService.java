package com.estebanmm13.pytra_api.games.service.genreService;

import com.estebanmm13.pytra_api.games.dto.genre.GenreRequestDto;
import com.estebanmm13.pytra_api.games.dto.genre.GenreResponseDto;

import java.util.List;

public interface GenreService {
    List<GenreResponseDto> findAllGenres();
    GenreCreationResult createIfMissing(GenreRequestDto genreRequestDto);
}
