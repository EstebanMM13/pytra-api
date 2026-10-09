package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import com.estebanmm13.pytra_api.stats.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final int MAX_LIMIT = 50;

    private final GameRepository gameRepository;
    private final SagaRepository sagaRepository;
    private final ExperienceRepository experienceRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;

    @Override
    public StatsSummaryDto getSummary(Long userId) {
        return new StatsSummaryDto(
                gameRepository.countByUserId(userId),
                sagaRepository.countByUserId(userId),
                experienceRepository.countByUserId(userId),
                experienceRepository.sumHoursByUserId(userId),
                onlinePlaytimeRepository.sumTotalHoursByUserId(userId),
                experienceRepository.countByUserIdAndPlatinumTrue(userId)
        );
    }

    @Override
    public List<YearStatDto> getByYear(Long userId) {
        List<YearStatDto> result = new ArrayList<>();
        for (Object[] row : experienceRepository.statsByYear(userId)) {
            result.add(new YearStatDto((Integer) row[0], (Double) row[1], (Long) row[2]));
        }
        return result;
    }

    @Override
    public List<SagaStatDto> getBySaga(Long userId) {
        List<Game> games = gameRepository.findAllByUserIdWithSagaAndGenres(userId);
        Map<Long, Double> hoursByGameId = hoursByGameId(userId);

        Map<Long, Accumulator> bySaga = new LinkedHashMap<>();
        for (Game game : games) {
            Long sagaId = game.getSaga() != null ? game.getSaga().getId() : null;
            String sagaName = game.getSaga() != null ? game.getSaga().getName() : "Sin saga";
            Accumulator acc = bySaga.computeIfAbsent(sagaId, id -> new Accumulator(sagaName));
            acc.count++;
            acc.hours += hoursByGameId.getOrDefault(game.getId(), 0.0);
        }

        List<SagaStatDto> result = new ArrayList<>();
        for (Map.Entry<Long, Accumulator> entry : bySaga.entrySet()) {
            Accumulator acc = entry.getValue();
            result.add(new SagaStatDto(entry.getKey(), acc.name, acc.count, acc.hours));
        }
        result.sort((a, b) -> Double.compare(b.getTotalHours(), a.getTotalHours()));
        return result;
    }

    @Override
    public List<GenreStatDto> getByGenre(Long userId) {
        List<Game> games = gameRepository.findAllByUserIdWithSagaAndGenres(userId);
        Map<Long, Double> hoursByGameId = hoursByGameId(userId);

        Map<Long, Accumulator> byGenre = new LinkedHashMap<>();
        for (Game game : games) {
            double gameHours = hoursByGameId.getOrDefault(game.getId(), 0.0);
            for (Genre genre : game.getGenres()) {
                Accumulator acc = byGenre.computeIfAbsent(genre.getId(), id -> new Accumulator(genre.getName()));
                acc.count++;
                acc.hours += gameHours;
            }
        }

        List<GenreStatDto> result = new ArrayList<>();
        for (Map.Entry<Long, Accumulator> entry : byGenre.entrySet()) {
            Accumulator acc = entry.getValue();
            result.add(new GenreStatDto(entry.getKey(), acc.name, acc.count, acc.hours));
        }
        result.sort((a, b) -> Double.compare(b.getTotalHours(), a.getTotalHours()));
        return result;
    }

    @Override
    public List<TopRatedExperienceDto> getTopRated(Long userId, int limit) {
        Map<Long, String> gameNames = gameNamesById(userId);

        List<TopRatedExperienceDto> result = new ArrayList<>();
        for (Experience experience : experienceRepository.findAllByUserIdAndRatingIsNotNullOrderByRatingDesc(userId)) {
            result.add(new TopRatedExperienceDto(
                    experience.getGameId(),
                    gameNames.getOrDefault(experience.getGameId(), "Unknown"),
                    experience.getRunLabel(),
                    experience.getRating()
            ));
        }
        return result.stream().limit(clampLimit(limit)).toList();
    }

    @Override
    public List<MostPlayedGameDto> getMostPlayedSingleplayer(Long userId, int limit) {
        Map<Long, String> gameNames = gameNamesById(userId);

        List<MostPlayedGameDto> result = new ArrayList<>();
        for (Object[] row : experienceRepository.sumHoursGroupedByGameId(userId)) {
            Long gameId = (Long) row[0];
            Double hours = (Double) row[1];
            result.add(new MostPlayedGameDto(gameId, gameNames.getOrDefault(gameId, "Unknown"), hours));
        }
        result.sort((a, b) -> Double.compare(b.getTotalHours(), a.getTotalHours()));
        return result.stream().limit(clampLimit(limit)).toList();
    }

    @Override
    public List<MostPlayedGameDto> getMostPlayedOnline(Long userId, int limit) {
        Map<Long, String> gameNames = gameNamesById(userId);

        List<MostPlayedGameDto> result = new ArrayList<>();
        for (OnlinePlaytime onlinePlaytime : onlinePlaytimeRepository.findAllByUserIdOrderByTotalHoursDesc(userId)) {
            result.add(new MostPlayedGameDto(
                    onlinePlaytime.getGameId(),
                    gameNames.getOrDefault(onlinePlaytime.getGameId(), "Unknown"),
                    onlinePlaytime.getTotalHours()
            ));
        }
        return result.stream().limit(clampLimit(limit)).toList();
    }

    private Map<Long, Double> hoursByGameId(Long userId) {
        Map<Long, Double> map = new HashMap<>();
        for (Object[] row : experienceRepository.sumHoursGroupedByGameId(userId)) {
            map.put((Long) row[0], (Double) row[1]);
        }
        return map;
    }

    private Map<Long, String> gameNamesById(Long userId) {
        Map<Long, String> map = new HashMap<>();
        for (Game game : gameRepository.findAllByUserId(userId)) {
            map.put(game.getId(), game.getName());
        }
        return map;
    }

    private int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    private static class Accumulator {
        final String name;
        long count;
        double hours;

        Accumulator(String name) {
            this.name = name;
        }
    }
}
