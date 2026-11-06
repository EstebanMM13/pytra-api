package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.OnlineDiaryMonthHours;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
import com.estebanmm13.pytra_api.experiences.model.Ratings;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.games.model.Game;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platforms.mapper.PlatformMapper;
import com.estebanmm13.pytra_api.stats.dto.MonthHoursDto;
import com.estebanmm13.pytra_api.stats.dto.YearExperienceDto;
import com.estebanmm13.pytra_api.stats.dto.YearNoteDto;
import com.estebanmm13.pytra_api.stats.dto.YearOnlineGameDto;
import com.estebanmm13.pytra_api.stats.dto.YearSummaryDto;
import com.estebanmm13.pytra_api.stats.repository.YearNoteRepository;
import com.estebanmm13.pytra_api.stats.service.StatsServiceImpl.Accumulator;

import java.time.LocalDate;
import java.util.*;

import static com.estebanmm13.pytra_api.stats.service.StatsServiceImpl.doubleOf;
import static com.estebanmm13.pytra_api.stats.service.StatsServiceImpl.hoursOf;
import static com.estebanmm13.pytra_api.stats.service.StatsServiceImpl.longOf;
import static com.estebanmm13.pytra_api.stats.service.StatsServiceImpl.requireValidYear;
import static com.estebanmm13.pytra_api.stats.service.StatsServiceImpl.sumHours;

/** A year's page: run and online totals, hours per month, the run table, the highlights and the note. */
final class YearSummaryBuilder {

    private final GameRepository gameRepository;
    private final ExperienceRepository experienceRepository;
    private final YearNoteRepository yearNoteRepository;
    private final DiaryEntryGameRepository diaryEntryGameRepository;

    YearSummaryBuilder(GameRepository gameRepository,
                       ExperienceRepository experienceRepository,
                       YearNoteRepository yearNoteRepository,
                       DiaryEntryGameRepository diaryEntryGameRepository) {
        this.gameRepository = gameRepository;
        this.experienceRepository = experienceRepository;
        this.yearNoteRepository = yearNoteRepository;
        this.diaryEntryGameRepository = diaryEntryGameRepository;
    }

    YearSummaryDto build(Long userId, int year) {
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
}
