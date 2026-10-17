package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.error.DuplicateResourceException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.games.mapper.GameMapper;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.model.Saga;
import com.estebanmm13.pytra_api.games.repository.GamePlatformLinkRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.GenreRepository;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Confirms a game discovered by a platform sync (Steam, PSN): applies the user's review and, when it
 * was still pending, imports the synced total of every platform link of the game.
 *
 * <p>Must run inside the caller's transaction, with the caller holding the user's sync guard.
 */
@Component
@RequiredArgsConstructor
public class PendingGameConfirmer {

    private final GameRepository gameRepository;
    private final GamePlatformLinkRepository gamePlatformLinkRepository;
    private final SagaRepository sagaRepository;
    private final GenreRepository genreRepository;
    private final ExternalPlaytimeWriter externalPlaytimeWriter;
    private final GameMapper gameMapper;

    public GameResponseDto confirm(Long gameId, GameRequestDto gameRequestDto, Long userId) {
        Game game = gameRepository.findByIdAndUserId(gameId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Game not found"));

        if (gameRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, gameRequestDto.getName(), gameId)) {
            throw new DuplicateResourceException("Game already exists");
        }

        boolean wasPending = game.getReviewStatus() == ReviewStatus.PENDING_REVIEW;

        game.setName(gameRequestDto.getName());
        game.setDeveloper(gameRequestDto.getDeveloper());
        game.setPublisher(gameRequestDto.getPublisher());
        game.setReleaseDate(gameRequestDto.getReleaseDate());
        game.setCategory(gameRequestDto.getCategory());
        game.setSaga(resolveSaga(gameRequestDto.getSagaId(), userId));
        // Keep the cover set at import (platform artwork) unless the client sends a new one.
        String cover = gameRequestDto.getCoverImageUrl();
        if (cover != null && !cover.isBlank()) {
            game.setCoverImageUrl(cover);
        }
        game.setGenres(resolveGenres(gameRequestDto.getGenreIds()));
        game.setReviewStatus(ReviewStatus.CONFIRMED);
        gameRepository.save(game);

        if (wasPending) {
            externalPlaytimeWriter.importTotalsOnConfirm(
                    gamePlatformLinkRepository.findAllByUserIdAndGameId(userId, game.getId()), game);
        }

        return gameMapper.toResponseDto(game);
    }

    private Saga resolveSaga(Long sagaId, Long userId) {
        if (sagaId == null) return null;
        return sagaRepository.findByIdAndUserId(sagaId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Saga not found"));
    }

    private Set<Genre> resolveGenres(Set<Long> genreIds) {
        if (genreIds == null || genreIds.isEmpty()) return new HashSet<>();
        List<Genre> foundGenres = genreRepository.findAllById(genreIds);
        if (foundGenres.size() != genreIds.size()) {
            throw new ResourceNotFoundException("Genre not found");
        }
        return new HashSet<>(foundGenres);
    }
}
