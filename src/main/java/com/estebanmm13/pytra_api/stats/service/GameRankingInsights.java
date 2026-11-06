package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.diary.repository.DiaryEntryGameRepository;
import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.repository.ExperienceRepository;
import com.estebanmm13.pytra_api.games.model.GameCategory;
import com.estebanmm13.pytra_api.games.model.ReviewStatus;
import com.estebanmm13.pytra_api.games.repository.GameAddedRow;
import com.estebanmm13.pytra_api.games.repository.GameLabel;
import com.estebanmm13.pytra_api.games.repository.GameRepository;
import com.estebanmm13.pytra_api.stats.dto.GameRankingsDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static com.estebanmm13.pytra_api.stats.service.InsightMath.clampLimit;
import static com.estebanmm13.pytra_api.stats.service.InsightMath.hoursOf;
import static com.estebanmm13.pytra_api.stats.service.InsightMath.round1;

/** Game rankings: most replayed, longest runs, short gems, most days played and longest pending. */
final class GameRankingInsights {

    /** "Short gems": games rated at least this, over at most SHORT_GEM_MAX_HOURS total hours. */
    private static final BigDecimal SHORT_GEM_MIN_RATING = new BigDecimal("8");
    /** Total hours (runs + online) cap of a short gem. */
    private static final double SHORT_GEM_MAX_HOURS = 15;

    private final InsightSources sources;
    private final DiaryEntryGameRepository diaryEntryGameRepository;
    private final ExperienceRepository experienceRepository;
    private final GameRepository gameRepository;

    GameRankingInsights(InsightSources sources,
                        DiaryEntryGameRepository diaryEntryGameRepository,
                        ExperienceRepository experienceRepository,
                        GameRepository gameRepository) {
        this.sources = sources;
        this.diaryEntryGameRepository = diaryEntryGameRepository;
        this.experienceRepository = experienceRepository;
        this.gameRepository = gameRepository;
    }

    GameRankingsDto rankings(Long userId, int limit) {
        int cap = clampLimit(limit);
        List<GameFacts> facts = sources.confirmedGameFacts(userId);
        Set<Long> confirmed = new HashSet<>();
        facts.forEach(game -> confirmed.add(game.gameId()));
        Map<Long, String> names = new HashMap<>();
        for (GameLabel label : gameRepository.findLabelsByUserId(userId)) {
            names.put(label.id(), label.name());
        }
        Map<Long, List<Experience>> runsByGame = new LinkedHashMap<>();
        for (Experience run : experienceRepository.findAllByUserId(userId)) {
            if (confirmed.contains(run.getGameId())) {
                runsByGame.computeIfAbsent(run.getGameId(), id -> new ArrayList<>()).add(run);
            }
        }

        return new GameRankingsDto(
                mostReplayed(runsByGame, names, cap),
                longestRuns(runsByGame, names, cap),
                shortGems(facts, names, cap),
                mostDaysPlayed(userId, confirmed, names, cap),
                longestPending(userId, runsByGame, names, cap));
    }

    private static List<GameRankingsDto.ReplayedGame> mostReplayed(Map<Long, List<Experience>> runsByGame,
                                                                   Map<Long, String> names, int cap) {
        List<GameRankingsDto.ReplayedGame> replayed = new ArrayList<>();
        runsByGame.forEach((gameId, runs) -> {
            if (runs.size() >= 2) {
                double hours = runs.stream().mapToDouble(InsightMath::hoursOf).sum();
                replayed.add(new GameRankingsDto.ReplayedGame(gameId, nameOf(gameId, names), runs.size(), round1(hours)));
            }
        });
        replayed.sort(Comparator.comparingLong(GameRankingsDto.ReplayedGame::runCount).reversed()
                .thenComparing(Comparator.comparingDouble(GameRankingsDto.ReplayedGame::hours).reversed())
                .thenComparing(GameRankingsDto.ReplayedGame::gameName, String.CASE_INSENSITIVE_ORDER));
        return replayed.stream().limit(cap).toList();
    }

    private static List<GameRankingsDto.LongRun> longestRuns(Map<Long, List<Experience>> runsByGame,
                                                             Map<Long, String> names, int cap) {
        return runsByGame.values().stream()
                .flatMap(List::stream)
                .filter(run -> hoursOf(run) > 0)
                .sorted(Comparator.comparingDouble(InsightMath::hoursOf).reversed()
                        .thenComparing(Experience::getId))
                .limit(cap)
                .map(run -> new GameRankingsDto.LongRun(run.getId(), run.getGameId(), nameOf(run.getGameId(), names),
                        run.getRunLabel(), round1(hoursOf(run))))
                .toList();
    }

    private static List<GameRankingsDto.ShortGem> shortGems(List<GameFacts> facts, Map<Long, String> names, int cap) {
        return facts.stream()
                .filter(game -> game.rating() != null && game.rating().compareTo(SHORT_GEM_MIN_RATING) >= 0
                        && game.hours() > 0 && game.hours() <= SHORT_GEM_MAX_HOURS)
                .map(game -> new GameRankingsDto.ShortGem(game.gameId(), nameOf(game.gameId(), names),
                        game.rating().setScale(2, RoundingMode.HALF_UP), round1(game.hours())))
                .sorted(Comparator.comparing(GameRankingsDto.ShortGem::rating).reversed()
                        .thenComparingDouble(GameRankingsDto.ShortGem::hours)
                        .thenComparing(GameRankingsDto.ShortGem::gameName, String.CASE_INSENSITIVE_ORDER))
                .limit(cap)
                .toList();
    }

    private List<GameRankingsDto.DaysPlayedGame> mostDaysPlayed(Long userId, Set<Long> confirmed,
                                                                Map<Long, String> names, int cap) {
        return diaryEntryGameRepository.countPlayedDaysByGame(userId).stream()
                .filter(row -> confirmed.contains(row.gameId()))
                .map(row -> new GameRankingsDto.DaysPlayedGame(row.gameId(), nameOf(row.gameId(), names), row.days()))
                .sorted(Comparator.comparingLong(GameRankingsDto.DaysPlayedGame::days).reversed()
                        .thenComparing(GameRankingsDto.DaysPlayedGame::gameName, String.CASE_INSENSITIVE_ORDER))
                .limit(cap)
                .toList();
    }

    /**
     * Pending games as the library's "Pendiente" tab sees them: games in the backlog (Game#backlog), or
     * SINGLEPLAYER games with no runs yet (online and hybrid games without runs are tracked by online playtime
     * instead); all waiting since the game was added.
     */
    private List<GameRankingsDto.PendingGame> longestPending(Long userId, Map<Long, List<Experience>> runsByGame,
                                                             Map<Long, String> names, int cap) {
        List<GameRankingsDto.PendingGame> pending = new ArrayList<>();
        for (GameAddedRow game : gameRepository.findAddedRowsByUserIdAndReviewStatus(userId, ReviewStatus.CONFIRMED)) {
            List<Experience> runs = runsByGame.get(game.gameId());
            boolean unplayed = (runs == null || runs.isEmpty()) && game.category() == GameCategory.SINGLEPLAYER;
            if (Boolean.TRUE.equals(game.backlog()) || unplayed) {
                pending.add(new GameRankingsDto.PendingGame(game.gameId(), nameOf(game.gameId(), names),
                        null, null, game.createdAt()));
            }
        }
        pending.sort(Comparator.comparing(GameRankingsDto.PendingGame::pendingSince,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(GameRankingsDto.PendingGame::gameId));
        return pending.stream().limit(cap).toList();
    }

    private static String nameOf(Long gameId, Map<Long, String> names) {
        return names.getOrDefault(gameId, "Unknown");
    }
}
