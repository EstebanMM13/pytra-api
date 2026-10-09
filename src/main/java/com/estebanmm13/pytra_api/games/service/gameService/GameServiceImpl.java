package com.estebanmm13.pytra_api.games.service.gameService;

import com.estebanmm13.pytra_api.error.DuplicateResourceException;
import com.estebanmm13.pytra_api.error.ResourceNotFoundException;
import com.estebanmm13.pytra_api.experiences.service.gameStats.GameExperienceStats;
import com.estebanmm13.pytra_api.experiences.service.gameStats.GameExperienceStatsCalculator;
import com.estebanmm13.pytra_api.games.dto.game.GameRequestDto;
import com.estebanmm13.pytra_api.games.dto.game.GameResponseDto;
import com.estebanmm13.pytra_api.games.mapper.GameMapper;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.model.Saga;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.GenreRepository;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private final GameRepository gameRepository;
    private final SagaRepository sagaRepository;
    private final GenreRepository genreRepository;
    private final GameMapper gameMapper;
    private final GameExperienceStatsCalculator gameExperienceStatsCalculator;

    @Override
    public List<GameResponseDto> findAllByUser(Long userId) {
        // Saga and genres fetched in the same query, aggregates in one more: no per-game queries.
        List<Game> games = gameRepository.findAllByUserIdWithSagaAndGenres(userId);
        Map<Long, GameExperienceStats> statsByGame = gameExperienceStatsCalculator.byGame(userId);
        List<GameResponseDto> gameResponseDtos = new ArrayList<>();
        for (Game game : games) {
            gameResponseDtos.add(gameMapper.toResponseDto(
                    game, statsByGame.getOrDefault(game.getId(), GameExperienceStats.EMPTY)));
        }
        return gameResponseDtos;
    }

    @Override
    public GameResponseDto findById(Long id, Long userId) {
        Optional<Game> game = gameRepository.findByIdAndUserId(id, userId);
        if (game.isPresent()) {
            return gameMapper.toResponseDto(game.get(), gameExperienceStatsCalculator.forGame(id, userId));
        } else {
            throw new ResourceNotFoundException("Game not found");
        }
    }

    @Override
    public GameResponseDto create(GameRequestDto gameRequestDto, Long userId) {
        if (gameRepository.existsByUserIdAndNameIgnoreCase(userId, gameRequestDto.getName())) {
            throw new DuplicateResourceException("Game already exists");
        }

        Saga saga = resolveSaga(gameRequestDto.getSagaId(), userId);
        Set<Genre> genres = resolveGenres(gameRequestDto.getGenreIds());

        Game game = Game.builder()
                .userId(userId)
                .name(gameRequestDto.getName())
                .developer(gameRequestDto.getDeveloper())
                .publisher(gameRequestDto.getPublisher())
                .releaseDate(gameRequestDto.getReleaseDate())
                .category(gameRequestDto.getCategory())
                .saga(saga)
                .coverImageUrl(gameRequestDto.getCoverImageUrl())
                .reviewStatus(ReviewStatus.CONFIRMED)
                .genres(genres)
                .build();

        gameRepository.save(game);
        return gameMapper.toResponseDto(game, GameExperienceStats.EMPTY);
    }

    @Override
    public GameResponseDto update(Long id, GameRequestDto gameRequestDto, Long userId) {
        // Ownership first: a game that isn't the caller's must be a plain 404, whatever the payload.
        Optional<Game> gameOptional = gameRepository.findByIdAndUserId(id, userId);
        if (gameOptional.isEmpty()) {
            throw new ResourceNotFoundException("Game not found");
        }

        if (gameRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, gameRequestDto.getName(), id)) {
            throw new DuplicateResourceException("Game already exists");
        }

        Saga saga = resolveSaga(gameRequestDto.getSagaId(), userId);
        Set<Genre> genres = resolveGenres(gameRequestDto.getGenreIds());

        Game game = gameOptional.get();
        game.setName(gameRequestDto.getName());
        game.setDeveloper(gameRequestDto.getDeveloper());
        game.setPublisher(gameRequestDto.getPublisher());
        game.setReleaseDate(gameRequestDto.getReleaseDate());
        game.setCategory(gameRequestDto.getCategory());
        game.setSaga(saga);
        game.setCoverImageUrl(gameRequestDto.getCoverImageUrl());
        game.setGenres(genres);

        gameRepository.save(game);
        return gameMapper.toResponseDto(game, gameExperienceStatsCalculator.forGame(id, userId));
    }

    @Override
    public void delete(Long id, Long userId) {
        Optional<Game> game = gameRepository.findByIdAndUserId(id, userId);
        if (game.isPresent()) {
            gameRepository.delete(game.get());
        } else {
            throw new ResourceNotFoundException("Game not found");
        }
    }

    private Saga resolveSaga(Long sagaId, Long userId) {
        if (sagaId == null) {
            return null;
        }
        return sagaRepository.findByIdAndUserId(sagaId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Saga not found"));
    }

    private Set<Genre> resolveGenres(Set<Long> genreIds) {
        if (genreIds == null || genreIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Genre> foundGenres = genreRepository.findAllById(genreIds);
        if (foundGenres.size() != genreIds.size()) {
            throw new ResourceNotFoundException("Genre not found");
        }
        return new HashSet<>(foundGenres);
    }
}
