package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.OnlineDiaryMonthHours;
import com.estebanmm13.pytra_api.diary.repository.OnlineDiaryYearHours;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.model.Ratings;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceTotals;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeTotals;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GameGenreRow;
import com.estebanmm13.pytra_api.games.repository.GameLabel;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.games.repository.GameSagaRow;
import com.estebanmm13.pytra_api.games.repository.SagaRepository;
import com.estebanmm13.pytra_api.error.DuplicateResourceException;
import com.estebanmm13.pytra_api.error.InvalidRequestException;
import com.estebanmm13.pytra_api.platforms.mapper.PlatformMapper;
import com.estebanmm13.pytra_api.stats.dto.*;
import com.estebanmm13.pytra_api.stats.model.YearNote;
import com.estebanmm13.pytra_api.stats.repository.YearNoteRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final int MAX_LIMIT = 50;
    static final int MIN_YEAR = 1970;
    private static final String YEAR_NOTE_UNIQUE_CONSTRAINT = "uq_year_notes_user_year";

    private final GameRepository gameRepository;
    private final SagaRepository sagaRepository;
    private final ExperienceRepository experienceRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;
    private final YearNoteRepository yearNoteRepository;
    private final DiaryEntryGameRepository diaryEntryGameRepository;

    @Override
    public StatsSummaryDto getSummary(Long userId) {
        // One aggregate query per table instead of one query per figure.
        ExperienceTotals runs = experienceRepository.totalsByUserId(userId);
        OnlinePlaytimeTotals online = onlinePlaytimeRepository.totalsByUserId(userId);
        return new StatsSummaryDto(
                // Pending games (imported, not reviewed yet) are not in the library.
                gameRepository.countByUserIdAndReviewStatus(userId, ReviewStatus.CONFIRMED),
                sagaRepository.countByUserId(userId),
                longOf(runs.getExperienceCount()),
                doubleOf(runs.getHoursSum()),
                doubleOf(online.getHoursSum()),
                // Online playtime has no year: its flags count in these all-time totals, not in year summaries.
                longOf(runs.getPlatinumCount()) + longOf(online.getPlatinumCount()),
                longOf(runs.getFullCompletionCount()) + longOf(online.getFullCompletionCount()),
                averageRating(runs, online),
                longOf(runs.getReplayCount()),
                longOf(runs.getCompletedCount()),
                longOf(runs.getAbandonedCount()),
                longOf(runs.getInProgressCount())
        );
    }

    /** Runs and online playtimes weigh the same: each rated one counts once. */
    private static BigDecimal averageRating(ExperienceTotals runs, OnlinePlaytimeTotals online) {
        return StatsMath.average(
                StatsMath.sum(runs.getRatingSum(), online.getRatingSum()),
                longOf(runs.getRatedCount()) + longOf(online.getRatedCount()));
    }

    private static long longOf(Number value) {
        return value != null ? value.longValue() : 0L;
    }

    private static double doubleOf(Number value) {
        return value != null ? value.doubleValue() : 0.0;
    }

    /**
     * Runs per year plus the online hours the diary dates in each year: a year with only online diary
     * hours has no runs, so its counts are 0 and its rating null.
     */
    @Override
    public List<YearStatDto> getByYear(Long userId) {
        SortedMap<Integer, List<Experience>> runsByYear = experiencesByYear(userId);
        Map<Integer, Double> onlineByYear = onlineDiaryHoursByYear(userId);
        SortedSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.addAll(runsByYear.keySet());
        years.addAll(onlineByYear.keySet());

        List<YearStatDto> result = new ArrayList<>();
        for (Integer year : years) {
            List<Experience> experiences = runsByYear.getOrDefault(year, List.of());
            double onlineHours = onlineByYear.getOrDefault(year, 0.0);
            result.add(new YearStatDto(
                    year,
                    sumHours(experiences) + onlineHours,
                    experiences.size(),
                    StatsMath.averageRating(experiences.stream().map(Experience::getRating).toList()),
                    onlineHours
            ));
        }
        return result;
    }

    @Override
    public List<Integer> getYears(Long userId) {
        SortedSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
        years.addAll(experiencesByYear(userId).keySet());
        years.addAll(onlineDiaryHoursByYear(userId).keySet());
        return new ArrayList<>(years);
    }

    /** Diary hours counted into online playtime, per entry year (online playtime itself is undated). */
    private Map<Integer, Double> onlineDiaryHoursByYear(Long userId) {
        Map<Integer, Double> byYear = new HashMap<>();
        for (OnlineDiaryYearHours row : diaryEntryGameRepository.sumCountedOnlineHoursByYear(userId)) {
            double hours = doubleOf(row.hours());
            if (row.year() != null && hours > 0) {
                byYear.merge(row.year(), hours, Double::sum);
            }
        }
        return byYear;
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
        long fullCompletions = 0;
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
            if (Boolean.TRUE.equals(experience.getFullCompletion())) fullCompletions++;

            Game game = games.get(experience.getGameId());
            rows.add(new YearExperienceDto(
                    experience.getId(),
                    experience.getGameId(),
                    game != null ? game.getName() : "Unknown",
                    game != null ? game.getCoverImageUrl() : null,
                    experience.getRunLabel(),
                    experience.getStatus(),
                    hours,
                    Ratings.normalize(experience.getRating()),
                    Boolean.TRUE.equals(experience.getPlatinum()),
                    Boolean.TRUE.equals(experience.getFullCompletion()),
                    PlatformMapper.legacyCodeOf(experience.getPlatform()),
                    PlatformMapper.idOf(experience.getPlatform()),
                    PlatformMapper.nameOf(experience.getPlatform()),
                    PlatformMapper.colorOf(experience.getPlatform()),
                    month,
                    experience.getStartDate(),
                    experience.getEndDate()
            ));
        }

        // Online hours dated by the diary: they add to the hours and months, never to the run counts.
        YearOnline online = yearOnline(userId, year, games);
        for (int month = 0; month < 12; month++) {
            hoursByMonth[month] += online.hoursByMonth()[month];
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
                sumHours(experiences) + online.hours(),
                experiences.size(),
                completed,
                abandoned,
                StatsMath.averageRating(experiences.stream().map(Experience::getRating).toList()),
                platinums,
                fullCompletions,
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
                        .orElse(new YearNoteDto(null, null, null)),
                online.hours(),
                online.games()
        );
    }

    /** A year's online diary hours: in total, per month (January first) and per game. */
    private record YearOnline(double hours, double[] hoursByMonth, List<YearOnlineGameDto> games) {
    }

    private YearOnline yearOnline(Long userId, int year, Map<Long, Game> games) {
        double total = 0;
        double[] byMonth = new double[12];
        // Per game: hours and diary days (Accumulator.count).
        Map<Long, Accumulator> byGame = new LinkedHashMap<>();
        for (OnlineDiaryMonthHours row : diaryEntryGameRepository.sumCountedOnlineHoursByMonthAndGame(
                userId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))) {
            double hours = doubleOf(row.hours());
            total += hours;
            if (row.month() != null) {
                byMonth[row.month() - 1] += hours;
            }
            Game game = games.get(row.gameId());
            Accumulator acc = byGame.computeIfAbsent(row.gameId(),
                    id -> new Accumulator(game != null ? game.getName() : "Unknown"));
            acc.hours += hours;
            acc.count += longOf(row.dayCount());
        }

        List<YearOnlineGameDto> onlineGames = new ArrayList<>();
        for (Map.Entry<Long, Accumulator> entry : byGame.entrySet()) {
            Game game = games.get(entry.getKey());
            Accumulator acc = entry.getValue();
            onlineGames.add(new YearOnlineGameDto(entry.getKey(), acc.name,
                    game != null ? game.getCoverImageUrl() : null, acc.hours, acc.count));
        }
        onlineGames.sort(Comparator
                .comparingDouble(YearOnlineGameDto::getHours).reversed()
                .thenComparing(YearOnlineGameDto::getGameId));
        return new YearOnline(total, byMonth, onlineGames);
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
            // Only the (user_id, year) race is a conflict; any other integrity error is a real bug.
            if (violates(e, YEAR_NOTE_UNIQUE_CONSTRAINT)) {
                throw new DuplicateResourceException("Year note was saved concurrently, retry");
            }
            throw e;
        }
    }

    private static boolean violates(DataIntegrityViolationException e, String constraintName) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && constraintName.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            if (cause.getMessage() != null && cause.getMessage().contains(constraintName)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
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
        Map<Long, GameLabel> games = gamesById(userId);

        List<Experience> experiences = new ArrayList<>(
                experienceRepository.findAllByUserIdAndStatus(userId, ExperienceStatus.EN_CURSO));
        // Most recently started first; undated runs last, most recently edited first.
        experiences.sort(Comparator
                .comparing(Experience::getStartDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Experience::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Experience::getId, Comparator.reverseOrder()));

        List<InProgressExperienceDto> result = new ArrayList<>();
        for (Experience experience : experiences) {
            GameLabel game = games.get(experience.getGameId());
            result.add(new InProgressExperienceDto(
                    experience.getId(),
                    experience.getGameId(),
                    game != null ? game.name() : "Unknown",
                    game != null ? game.coverImageUrl() : null,
                    experience.getRunLabel(),
                    PlatformMapper.legacyCodeOf(experience.getPlatform()),
                    PlatformMapper.idOf(experience.getPlatform()),
                    PlatformMapper.nameOf(experience.getPlatform()),
                    PlatformMapper.colorOf(experience.getPlatform()),
                    experience.getStartDate(),
                    hoursOf(experience),
                    experience.getUpdatedAt()
            ));
        }
        return result;
    }

    @Override
    public List<SagaStatDto> getBySaga(Long userId) {
        // Projection rows (game id + saga), in game id order: no entities nor genres are loaded.
        List<GameSagaRow> games = gameRepository.findSagaRowsByUserIdAndReviewStatus(userId, ReviewStatus.CONFIRMED);
        Map<Long, Double> hoursByGameId = hoursByGameId(userId);

        Map<Long, Accumulator> bySaga = new LinkedHashMap<>();
        for (GameSagaRow game : games) {
            Long sagaId = game.sagaId();
            String sagaName = sagaId != null ? game.sagaName() : "Sin saga";
            Accumulator acc = bySaga.computeIfAbsent(sagaId, id -> new Accumulator(sagaName));
            acc.count++;
            acc.hours += hoursByGameId.getOrDefault(game.gameId(), 0.0);
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
        // One projection row per (game, genre) pair, in game id order: no entities are loaded.
        List<GameGenreRow> gameGenres = gameRepository.findGenreRowsByUserIdAndReviewStatus(userId, ReviewStatus.CONFIRMED);
        Map<Long, Double> hoursByGameId = hoursByGameId(userId);

        Map<Long, Accumulator> byGenre = new LinkedHashMap<>();
        for (GameGenreRow gameGenre : gameGenres) {
            Accumulator acc = byGenre.computeIfAbsent(gameGenre.genreId(), id -> new Accumulator(gameGenre.genreName()));
            acc.count++;
            acc.hours += hoursByGameId.getOrDefault(gameGenre.gameId(), 0.0);
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
                    Ratings.normalize(experience.getRating()),
                    experience.getId(),
                    false
            ));
        }
        for (OnlinePlaytime onlinePlaytime : onlinePlaytimeRepository.findAllByUserIdAndGeneralRatingIsNotNull(userId)) {
            result.add(new TopRatedExperienceDto(
                    onlinePlaytime.getGameId(),
                    gameNames.getOrDefault(onlinePlaytime.getGameId(), "Unknown"),
                    null,
                    Ratings.normalize(onlinePlaytime.getGeneralRating()),
                    null,
                    true
            ));
        }
        // Stable sort: on equal ratings runs keep their repository order and come before online entries.
        result.sort(Comparator.comparing(TopRatedExperienceDto::getRating, Comparator.reverseOrder()));
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

    /** Run hours plus online hours per game, matching the library's totalHours. */
    private Map<Long, Double> hoursByGameId(Long userId) {
        Map<Long, Double> map = new HashMap<>();
        for (Object[] row : experienceRepository.sumHoursGroupedByGameId(userId)) {
            map.merge((Long) row[0], (Double) row[1], Double::sum);
        }
        for (Object[] row : onlinePlaytimeRepository.sumTotalHoursGroupedByGameId(userId)) {
            map.merge((Long) row[0], (Double) row[1], Double::sum);
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

    private Map<Long, GameLabel> gamesById(Long userId) {
        Map<Long, GameLabel> map = new HashMap<>();
        for (GameLabel game : gameRepository.findLabelsByUserId(userId)) {
            map.put(game.id(), game);
        }
        return map;
    }

    private Map<Long, String> gameNamesById(Long userId) {
        Map<Long, String> map = new HashMap<>();
        for (GameLabel game : gameRepository.findLabelsByUserId(userId)) {
            map.put(game.id(), game.name());
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
