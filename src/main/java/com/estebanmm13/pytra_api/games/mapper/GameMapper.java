package com.estebanmm13.pytra_api.games.mapper;

import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.games.model.Game;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameMapper {

    private final GenreMapper genreMapper;

    public GameResponseDto toResponseDto(Game game) {
        if (game == null) return null;

        return new GameResponseDto(
                game.getId(),
                game.getName(),
                game.getDeveloper(),
                game.getPublisher(),
                game.getReleaseDate(),
                game.getCategory(),
                game.getSaga() != null ? game.getSaga().getId() : null,
                game.getSaga() != null ? game.getSaga().getName() : null,
                game.getCoverImageUrl(),
                game.getReviewStatus(),
                game.getGenres().stream()
                        .map(genreMapper::toResponseDto)
                        .toList(),
                game.getUpdatedAt()
        );
    }
}
