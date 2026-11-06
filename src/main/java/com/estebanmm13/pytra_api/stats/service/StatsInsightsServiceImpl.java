package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.config.AppTimeZone;
import com.estebanmm13.pytra_api.diary.repository.DiaryDayTotals;
import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.diary.repository.OnlineDiaryYearGameHours;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod;
import com.estebanmm13.pytra_api.experiences.model.OnlinePlaytime;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.experiences.repository.OnlinePlaytimeRepository;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.platforms.mapper.PlatformMapper;
import com.estebanmm13.pytra_api.platforms.model.Platform;
import com.estebanmm13.pytra_api.stats.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static com.estebanmm13.pytra_api.stats.service.InsightMath.doubleOf;
import static com.estebanmm13.pytra_api.stats.service.InsightMath.hoursOf;

/**
 * Loads each breakdown with a few whole-user queries (runs, online playtimes, diary aggregates, projections)
 * and aggregates in memory, so no endpoint issues one query per game, run or platform. Read-only
 * transactions keep each request on a single pooled connection.
 *
 * <p>Counting rules shared with the other stats: run figures cover every run of the user (like the summary);
 * game-grouped figures (genres, developers, release eras) cover CONFIRMED games only (like by-genre and
 * by-saga), since pending imports are not in the library yet.
 *
 * <p>Each larger breakdown lives in its own {@code *Insights} class; this service is their read-only
 * transaction boundary.
 */
@Service
public class StatsInsightsServiceImpl implements StatsInsightsService {

    private final DiaryEntryGameRepository diaryEntryGameRepository;
    private final ExperienceRepository experienceRepository;
    private final OnlinePlaytimeRepository onlinePlaytimeRepository;
    private final Clock clock;
    private final PlatformInsights platformInsights;
    private final StudioInsights studioInsights;
    private final CompletionInsights completionInsights;
    private final RatingInsights ratingInsights;
    private final GameRankingInsights gameRankingInsights;

    @Autowired
    public StatsInsightsServiceImpl(DiaryEntryGameRepository diaryEntryGameRepository,
                                    ExperienceRepository experienceRepository,
                                    OnlinePlaytimeRepository onlinePlaytimeRepository,
                                    GameRepository gameRepository,
                                    AppTimeZone appTimeZone) {
        // "Today" of the activity streaks is the users' day, not the server's (UTC in production).
        this(diaryEntryGameRepository, experienceRepository, onlinePlaytimeRepository, gameRepository,
                appTimeZone.clock());
    }

    StatsInsightsServiceImpl(DiaryEntryGameRepository diaryEntryGameRepository,
                             ExperienceRepository experienceRepository,
                             OnlinePlaytimeRepository onlinePlaytimeRepository,
                             GameRepository gameRepository,
                             Clock clock) {
        this.diaryEntryGameRepository = diaryEntryGameRepository;
        this.experienceRepository = experienceRepository;
        this.onlinePlaytimeRepository = onlinePlaytimeRepository;
        this.clock = clock;
        InsightSources sources = new InsightSources(experienceRepository, onlinePlaytimeRepository, gameRepository);
        this.platformInsights = new PlatformInsights(diaryEntryGameRepository, experienceRepository, onlinePlaytimeRepository);
        this.studioInsights = new StudioInsights(sources);
        this.completionInsights = new CompletionInsights(sources, gameRepository);
        this.ratingInsights = new RatingInsights(sources);
        this.gameRankingInsights = new GameRankingInsights(sources, diaryEntryGameRepository, experienceRepository, gameRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityStatsDto getActivity(Long userId, Integer year) {
        LocalDate today = LocalDate.now(clock);
        int effectiveYear = year != null ? year : today.getYear();
        StatsServiceImpl.requireValidYear(effectiveYear);

        List<DiaryDayTotals> rows = diaryEntryGameRepository.sumHoursByDay(
                userId, LocalDate.of(effectiveYear, 1, 1), LocalDate.of(effectiveYear, 12, 31));

        BigDecimal totalHours = BigDecimal.ZERO;
        long[] daysByWeekday = new long[7];
        BigDecimal[] hoursByWeekday = new BigDecimal[7];
        Arrays.fill(hoursByWeekday, BigDecimal.ZERO);
        List<ActivityStatsDto.Day> days = new ArrayList<>(rows.size());
        for (DiaryDayTotals row : rows) {
            BigDecimal hours = row.hours() != null ? row.hours() : BigDecimal.ZERO;
            totalHours = totalHours.add(hours);
            int weekday = row.date().getDayOfWeek().getValue() - 1;
            daysByWeekday[weekday]++;
            hoursByWeekday[weekday] = hoursByWeekday[weekday].add(hours);
            days.add(new ActivityStatsDto.Day(row.date(), hours.doubleValue(),
                    row.gameCount() != null ? row.gameCount() : 0L));
        }

        List<ActivityStatsDto.Weekday> weekdays = new ArrayList<>(7);
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            int index = dayOfWeek.getValue() - 1;
            weekdays.add(new ActivityStatsDto.Weekday(dayOfWeek.getValue(), dayOfWeek.name(),
                    daysByWeekday[index], hoursByWeekday[index].doubleValue()));
        }

        List<LocalDate> playedDates = diaryEntryGameRepository.findPlayedDates(userId);
        return new ActivityStatsDto(
                effectiveYear,
                totalHours.doubleValue(),
                days.size(),
                days,
                ActivityStreaks.current(playedDates, today),
                ActivityStreaks.longest(playedDates),
                weekdays);
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformStatsDto getPlatforms(Long userId, Integer year) {
        return platformInsights.platforms(userId, year);
    }

    @Override
    @Transactional(readOnly = true)
    public DeveloperStatsDto getDevelopers(Long userId, int limit, int minRatedGames) {
        return studioInsights.developers(userId, limit, minRatedGames);
    }

    @Override
    @Transactional(readOnly = true)
    public DeveloperStatsDto getPublishers(Long userId, int limit, int minRatedGames) {
        return studioInsights.publishers(userId, limit, minRatedGames);
    }

    @Override
    @Transactional(readOnly = true)
    public ReleaseEraStatsDto getReleaseEras(Long userId) {
        return studioInsights.releaseEras(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public CompletionStatsDto getCompletion(Long userId, Integer year) {
        return completionInsights.completion(userId, year);
    }

    @Override
    @Transactional(readOnly = true)
    public PlayFactsDto getPlayFacts(Long userId) {
        List<PlayFactsDto.RunFact> runs = new ArrayList<>();
        for (Experience run : experienceRepository.findAllByUserId(userId)) {
            runs.add(new PlayFactsDto.RunFact(run.getId(), run.getGameId(),
                    run.getPlatform() != null ? run.getPlatform().getId() : null,
                    ExperiencePeriod.yearOf(run), run.getStatus(), StatsMath.round2(hoursOf(run)), CompletionInsights.daysToFinish(run)));
        }
        runs.sort(Comparator.comparing(PlayFactsDto.RunFact::experienceId, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<Long, List<Long>> onlinePlatformIds = new HashMap<>();
        for (OnlinePlaytime playtime : onlinePlaytimeRepository.findAllByUserId(userId)) {
            List<Platform> platforms = new ArrayList<>(playtime.getPlatforms());
            platforms.sort(PlatformMapper.CATALOG_ORDER);
            onlinePlatformIds.put(playtime.getGameId(), platforms.stream().map(Platform::getId).toList());
        }
        // Same rows as the per-year platform hours: a game "was played online" in a year with counted hours.
        List<PlayFactsDto.OnlineYear> onlineYears = new ArrayList<>();
        for (OnlineDiaryYearGameHours row : diaryEntryGameRepository.sumCountedOnlineHoursByYearAndGame(userId)) {
            if (row.year() != null && doubleOf(row.hours()) > 0) {
                onlineYears.add(new PlayFactsDto.OnlineYear(row.gameId(), row.year(),
                        onlinePlatformIds.getOrDefault(row.gameId(), List.of())));
            }
        }
        onlineYears.sort(Comparator.comparing(PlayFactsDto.OnlineYear::gameId).thenComparingInt(PlayFactsDto.OnlineYear::year));
        return new PlayFactsDto(runs, onlineYears);
    }

    @Override
    @Transactional(readOnly = true)
    public RatingStatsDto getRatings(Long userId, Integer year) {
        return ratingInsights.ratings(userId, year);
    }

    @Override
    @Transactional(readOnly = true)
    public GameRankingsDto getGameRankings(Long userId, int limit) {
        return gameRankingInsights.rankings(userId, limit);
    }
}
