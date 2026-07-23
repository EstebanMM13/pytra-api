package com.estebanmm13.pytra_api.games.mapper;

import com.estebanmm13.pytra_api.games.dto.genre.GenreResponseDto;
import com.estebanmm13.pytra_api.games.model.Genre;
import org.springframework.stereotype.Component;

@Component
public class GenreMapper {

    public GenreResponseDto toResponseDto( Genre genre) {
        if(genre == null) return null;
        return new  GenreResponseDto(
                genre.getId(),
                genre.getName()
        );
    }

}
