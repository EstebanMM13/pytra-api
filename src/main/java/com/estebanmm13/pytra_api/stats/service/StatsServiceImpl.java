package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.Genre;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import com.estebanmm13.pytra_api.error.DuplicateResourceException;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.stats.dto.*;
import com.estebanmm13.pytra_api.stats.model.YearNote;
import com.estebanmm13.pytra_api.stats.repository.YearNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final int MAX_LIMIT = 50;
    static final int MIN_YEAR = 1970;

    private final GameRepository gameRepository;
    private final SagaRepository sagaRepository;
    private final ExperienceRepository experienceRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;
    private final YearNoteRepository yearNoteRepository;

    @Override
    public StatsSummaryDto getSummary(Long userId) {
        return new StatsSummaryDto(
                gameRepository.countByUserId(userId),
                sagaRepository.countByUserId(userId),
                experienceRepository.countByUserId(userId),
                experienceRepository.sumHoursByUserId(userId),
                onlinePlaytimeRepository.sumTotalHoursByUserId(userId),
                experienceRepository.countByUserIdAndPlatinumTrue(userId),
                StatsMath.round2(experienceRepository.averageRatingByUserId(userId)),
                experienceRepository.countByUserIdAndReplayTrue(userId),
                experienceRepository.countByUserIdAndStatus(userId, ExperienceStatus.COMPLETADO),
                experienceRepository.countByUserIdAndStatus(userId, ExperienceStatus.ABANDONADO),
                experienceRepository.countByUserIdAndStatus(userId, ExperienceStatus.EN_CURSO)
        );
    }

    @Override
    public List<YearStatDto> getByYear(Long userId) {
        List<YearStatDto> result = new ArrayList<>();
        for (Map.Entry<Integer, List<Experience>> entry : experiencesByYear(userId).entrySet()) {
            List<Experience> experiences = entry.getValue();
            result.add(new YearStatDto(
                    entry.getKey(),
                    sumHours(experiences),
                    experiences.size(),
                    StatsMath.averageRating(experiences.stream().map(Experience::getRating).toList())
            ));
        }
        return result;
    }

    @Override
    public List<Integer> getYears(Long userId) {
        return new ArrayList<>(experiencesByYear(userId).keySet());
    }

    @Override
    @Transactional(readOnly = true)
    public YearSummaryDto getYearSummary(Long userId, int year) {
        requireValidYear(year);
        List<Experience> allExperiences = experienceRepository.findAllByUserId(userId);
        List<Experience> experiences = allExperiences.stream()
                .filter(experience -> Objects.equals(ExperiencePeriod.yearOf(experience), year))
                .toList();
        // Saga and genres fetched eagerly: the highlights group by them.
        Map<Long, Game> games = new HashMap<>();
        for (Game game : gameRepository.findAllByUserIdWithSagaAndGenres(userId)) {
            games.put(game.getId(), game);
        }
        YearHighlightsCalculator.YearHighlights highlights =
                YearHighlightsCalculator.compute(year, allExperiences, games);

        double[] hoursByMonth = new double[12];
        double hoursWithoutMonth = 0;
        long completed = 0;
        long abandoned = 0;
        long platinums = 0;
        List<YearExperienceDto> rows = new ArrayList<>();

        for (Experience experience : experiences) {
            double hours = hoursOf(experience);
            Integer month = ExperiencePeriod.monthOf(experience);
            if (month != null) {
                hoursByMonth[month - 1] += hours;
            } else {
                hoursWithoutMonth += hours;
            }
            if (experience.getStatus() == ExperienceStatus.COMPLETADO) completed++;
            if (experience.getStatus() == ExperienceStatus.ABANDONADO) abandoned++;
            if (Boolean.TRUE.equals(experience.getPlatinum())) platinums++;

            Game game = games.get(experience.getGameId());
            rows.add(new YearExperienceDto(
                    experience.getId(),
                    experience.getGameId(),
                    game != null ? game.getName() : "Unknown",
                    game != null ? game.getCoverImageUrl() : null,
                    experience.getRunLabel(),
                    experience.getStatus(),
                    hours,
                    experience.getRating(),
                    Boolean.TRUE.equals(experience.getPlatinum()),
                    experience.getPlatform(),
                    month,
                    experience.getStartDate(),
                    experience.getEndDate()
            ));
        }

        rows.sort(Comparator
                .comparing(YearExperienceDto::getRating, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(YearExperienceDto::getHours, Comparator.reverseOrder())
                .thenComparing(YearExperienceDto::getExperienceId));

        List<MonthHoursDto> months = new ArrayList<>(12);
        for (int month = 1; month <= 12; month++) {
            months.add(new MonthHoursDto(month, hoursByMonth[month - 1]));
        }

        return new YearSummaryDto(
                year,
                sumHours(experiences),
                experiences.size(),
                completed,
                abandoned,
                StatsMath.averageRating(experiences.stream().map(Experience::getRating).toList()),
                platinums,
                hoursWithoutMonth,
                months,
                rows,
                highlights.goty(),
                highlights.topRated(),
                highlights.mostPlayed(),
                highlights.topSagas(),
                highlights.topGenres(),
                highlights.surprises(),
                highlights.disappointments(),
                yearNoteRepository.findByUserIdAndYear(userId, year)
                        .map(StatsServiceImpl::toNoteDto)
                        .orElse(new YearNoteDto(null, null, null))
        );
    }

    @Override
    @Transactional
    public YearNoteDto upsertYearNote(Long userId, int year, YearNoteRequestDto request) {
        requireValidYear(year);
        YearNote note = yearNoteRepository.findByUserIdAndYear(userId, year)
                .orElseGet(() -> YearNote.builder().userId(userId).year(year).build());
        note.setSummary(blankToNull(request.getSummary()));
        note.setHighlights(blankToNull(request.getHighlights()));
        try {
            // Flush now so two concurrent first saves surface as 409 instead of a 500 at commit.
            return toNoteDto(yearNoteRepository.saveAndFlush(note));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("Year note was saved concurrently, retry");
        }
    }

    /** Years accepted by the year endpoints: 1970 to next year. */
    static void requireValidYear(int year) {
        if (year < MIN_YEAR || year > Year.now().getValue() + 1) {
            throw new InvalidRequestException(InvalidRequestException.INVALID_YEAR);
        }
    }

    private static YearNoteDto toNoteDto(YearNote note) {
        return new YearNoteDto(note.getSummary(), note.getHighlights(), note.getUpdatedAt());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    @Override
    public List<InProgressExperienceDto> getInProgress(Long userId) {
        Map<Long, Game> games = gamesById(userId);

        List<Experience> experiences = new ArrayList<>(
                experienceRepository.findAllByUserIdAndStatus(userId, ExperienceStatus.EN_CURSO));
        // Most recently started first; undated runs last, most recently edited first.
        experiences.sort(Comparator
                .comparing(Experience::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Experience::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Experience::getId, Comparator.reverseOrder()));

        List<InProgressExperienceDto> result = new ArrayList<>();
        for (Experience experience : experiences) {
            Game game = games.get(experience.getGameId());
            result.add(new InProgressExperienceDto(
                    experience.getId(),
                    experience.getGameId(),
                    game != null ? game.getName() : "Unknown",
                    game != null ? game.getCoverImageUrl() : null,
                    experience.getRunLabel(),
                    experience.getPlatform(),
                    experience.getStartDate(),
                    hoursOf(experience),
                    experience.getUpdatedAt()
            ));
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

    /** Experiences grouped by {@link ExperiencePeriod#yearOf}, newest year first; undated ones are dropped. */
    private SortedMap<Integer, List<Experience>> experiencesByYear(Long userId) {
        SortedMap<Integer, List<Experience>> byYear = new TreeMap<>(Comparator.reverseOrder());
        for (Experience experience : experienceRepository.findAllByUserId(userId)) {
            Integer year = ExperiencePeriod.yearOf(experience);
            if (year != null) {
                byYear.computeIfAbsent(year, y -> new ArrayList<>()).add(experience);
            }
        }
        return byYear;
    }

    private static double sumHours(List<Experience> experiences) {
        double total = 0;
        for (Experience experience : experiences) {
            total += hoursOf(experience);
        }
        return total;
    }

    private static double hoursOf(Experience experience) {
        return experience.getHours() != null ? experience.getHours() : 0.0;
    }

    private Map<Long, Game> gamesById(Long userId) {
        Map<Long, Game> map = new HashMap<>();
        for (Game game : gameRepository.findAllByUserId(userId)) {
            map.put(game.getId(), game);
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
