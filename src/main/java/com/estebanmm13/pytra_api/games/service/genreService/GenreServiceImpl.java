package com.estebanmm13.pytra_api.games.service.genreService;

import com.estebanmm13.pytra_api.games.dto.genre.GenreRequestDto;
import com.estebanmm13.pytra_api.games.dto.genre.GenreResponseDto;
import com.estebanmm13.pytra_api.games.mapper.GenreMapper;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.repository.GenreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GenreServiceImpl implements GenreService {

    private final GenreRepository genreRepository;
    private final GenreMapper genreMapper;

    @Override
    public List<GenreResponseDto> findAllGenres() {
        List<Genre> genres = genreRepository.findAll();
        List<GenreResponseDto> genreResponseDtos = new ArrayList<>();
        for (Genre genre : genres) {
            genreResponseDtos.add(genreMapper.toResponseDto(genre));
        }
        return genreResponseDtos;
    }

    @Override
    public GenreCreationResult createIfMissing(GenreRequestDto genreRequestDto) {
        String genreNameNormalized = genreRequestDto.getName().toUpperCase();
        Optional<Genre> genre = genreRepository.findByNameIgnoreCase(genreNameNormalized);

        if (genre.isPresent()) {
            return new GenreCreationResult(genreMapper.toResponseDto(genre.get()),false) ;
        }else {
            Genre genre1 = Genre.builder().name(genreNameNormalized).build();
            genreRepository.save(genre1);
            return new GenreCreationResult(genreMapper.toResponseDto(genre1),true) ;
        }
    }
}
