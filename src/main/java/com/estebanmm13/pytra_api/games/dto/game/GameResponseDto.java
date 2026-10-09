package com.estebanmm13.pytra_api.games.dto.game;

import com.estebanmm13.pytra_api.games.dto.genre.GenreResponseDto;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class GameResponseDto {
    private Long id;
    private String name;
    private String developer;
    private String publisher;
    private LocalDate releaseDate;
    private GameCategory category;
    private Long sagaId;
    private String sagaName;
    private String coverImageUrl;
    private ReviewStatus reviewStatus;
    private List<GenreResponseDto> genres;
    private LocalDateTime updatedAt;
}
