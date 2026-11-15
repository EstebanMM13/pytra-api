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
import com.estebanmm13.pytra_api.stats.dto.YearRunRole;
import com.estebanmm13.pytra_api.stats.dto.YearSummaryDto;
import com.estebanmm13.pytra_api.stats.repository.YearNoteRepository;
import com.estebanmm13.pytra_api.stats.service.StatsServiceImpl.Accumulator;

import java.time.Clock;
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
    private final Clock clock;

    YearSummaryBuilder(GameRepository gameRepository,
                       ExperienceRepository experienceRepository,
                       YearNoteRepository yearNoteRepository,
                       DiaryEntryGameRepository diaryEntryGameRepository,
                       Clock clock) {
        this.clock = clock;
        this.gameRepository = gameRepository;
        this.experienceRepository = experienceRepository;
        this.yearNoteRepository = yearNoteRepository;
        this.diaryEntryGameRepository = diaryEntryGameRepository;
    }

    YearSummaryDto build(Long userId, int year) {
        requireValidYear(year);
        List<Experience> allExperiences = experienceRepository.findAllByUserId(userId);
        // Finished (credits or abandonment) in the year: completed, abandoned and the rating.
        List<Experience> experiences = allExperiences.stream()
                .filter(experience -> Objects.equals(ExperiencePeriod.yearOf(experience), year))
                .toList();
        // Saga and genres fetched eagerly: the highlights group by them.
        Map<Long, Game> games = new HashMap<>();
        for (Game game : gameRepository.findAllByUserIdWithSagaAndGenres(userId)) {
            games.put(game.getId(), game);
        }
        // Hours follow the days played (RunHours); the run table lists every run played in the year.
        RunHours runHours = RunHours.of(allExperiences, diaryEntryGameRepository.sumCountedRunHoursByDay(userId),
                LocalDate.now(clock));
        List<Experience> played = allExperiences.stream()
                .filter(experience -> Objects.equals(ExperiencePeriod.yearOf(experience), year)
                        || runHours.playedIn(experience, year))
                .toList();
        YearHighlightsCalculator.YearHighlights highlights =
                YearHighlightsCalculator.compute(year, allExperiences, games, run -> runHours.inYear(run, year));

        double[] hoursByMonth = runHours.byMonth(year);
        double hoursWithoutMonth = runHours.withoutMonth(year);
        long completed = 0;
        long abandoned = 0;
        long platinums = 0;
        long fullCompletions = 0;
        List<YearExperienceDto> rows = new ArrayList<>();

        for (Experience experience : experiences) {
            if (experience.getStatus() == ExperienceStatus.COMPLETADO) completed++;
            if (experience.getStatus() == ExperienceStatus.ABANDONADO) abandoned++;
        }
        for (Experience experience : allExperiences) {
            if (!Objects.equals(ExperiencePeriod.achievementYearOf(experience), year)) continue;
            if (Boolean.TRUE.equals(experience.getPlatinum())) platinums++;
            if (Boolean.TRUE.equals(experience.getFullCompletion())) fullCompletions++;
        }

        for (Experience experience : played) {
            double hours = hoursOf(experience);
            boolean creditsYear = Objects.equals(ExperiencePeriod.yearOf(experience), year);
            Integer month = creditsYear ? ExperiencePeriod.monthOf(experience) : runHours.lastMonth(experience, year);
            boolean achievedHere = Objects.equals(ExperiencePeriod.achievementYearOf(experience), year);

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
                    achievedHere && Boolean.TRUE.equals(experience.getPlatinum()),
                    achievedHere && Boolean.TRUE.equals(experience.getFullCompletion()),
                    PlatformMapper.legacyCodeOf(experience.getPlatform()),
                    PlatformMapper.idOf(experience.getPlatform()),
                    PlatformMapper.nameOf(experience.getPlatform()),
                    PlatformMapper.colorOf(experience.getPlatform()),
                    month,
                    experience.getStartDate(),
                    experience.getEndDate(),
                    roleIn(experience, year),
                    StatsMath.round2(runHours.inYear(experience, year)),
                    Boolean.TRUE.equals(experience.getReplay())
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
            months.add(new MonthHoursDto(month, StatsMath.round2(hoursByMonth[month - 1])));
        }

        return new YearSummaryDto(
                year,
                StatsMath.round2(runHours.inYear(year) + online.hours()),
                played.size(),
                completed,
                abandoned,
                StatsMath.averageRating(experiences.stream().map(Experience::getRating).toList()),
                platinums,
                fullCompletions,
                StatsMath.round2(hoursWithoutMonth),
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

    /** What a run was in {@code year}: its credits or abandonment, its postgame, or still being played. */
    static YearRunRole roleIn(Experience run, int year) {
        Integer creditsYear = ExperiencePeriod.yearOf(run);
        if (Objects.equals(creditsYear, year)) {
            return switch (run.getStatus()) {
                case COMPLETADO -> YearRunRole.COMPLETED;
                case ABANDONADO -> YearRunRole.ABANDONED;
                default -> YearRunRole.PLAYING;
            };
        }
        if (run.getStatus() == ExperienceStatus.COMPLETADO && creditsYear != null && year > creditsYear) {
            return YearRunRole.POSTGAME;
        }
        return YearRunRole.PLAYING;
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
