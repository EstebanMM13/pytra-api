package com.estebanmm13.pytra_api.games.mapper;

import com.estebanmm13.pytra_api.experiences.service.gameStats.GameExperienceStats;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.games.model.Game;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GameMapper {

    private final GenreMapper genreMapper;

    /** Without library aggregates (they stay null). */
    public GameResponseDto toResponseDto(Game game) {
        return toResponseDto(game, null);
    }

    public GameResponseDto toResponseDto(Game game, GameExperienceStats stats) {
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
                game.getUpdatedAt(),
                stats != null ? stats.experienceCount() : null,
                stats != null ? stats.totalHours() : null,
                stats != null ? stats.bestRating() : null,
                stats != null ? stats.lastExperienceStatus() : null,
                stats != null ? stats.lastPlayedYear() : null,
                stats != null ? stats.hasPlatinum() : null,
                stats != null ? stats.platforms() : null,
                stats != null ? stats.lastPlayedAt() : null
        );
    }
}
