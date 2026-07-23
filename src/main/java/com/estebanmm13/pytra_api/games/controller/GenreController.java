package com.estebanmm13.pytra_api.games.controller;

import com.estebanmm13.pytra_api.games.dto.genre.GenreRequestDto;
import com.estebanmm13.pytra_api.games.dto.genre.GenreResponseDto;
import com.estebanmm13.pytra_api.games.service.genreService.GenreCreationResult;
import com.estebanmm13.pytra_api.games.service.genreService.GenreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/genres")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    @GetMapping()
    public ResponseEntity<List<GenreResponseDto>> findAllGenres() {
        return ResponseEntity.ok(genreService.findAllGenres());
    }

    @PostMapping()
    public ResponseEntity<GenreResponseDto> createGenre(
            @Valid @RequestBody GenreRequestDto genreRequestDto) {
        GenreCreationResult genreCreationResult = genreService.createIfMissing(genreRequestDto);
        if (genreCreationResult.getCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(genreCreationResult.getGenreResponseDto());
        }else  {
            return ResponseEntity.ok(genreCreationResult.getGenreResponseDto());
        }
    }
}
