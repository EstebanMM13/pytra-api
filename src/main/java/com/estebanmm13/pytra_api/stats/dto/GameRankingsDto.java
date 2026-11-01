package com.estebanmm13.pytra_api.stats.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Game rankings of the stats "Games" screen. Every list covers CONFIRMED games only and holds at most the
 * requested limit (1-50). The most played and top rated rankings live in their own endpoints.
 *
 * @param mostReplayed   games with 2+ runs that are not PENDIENTE, most runs first (ties: more run hours,
 *                       then name)
 * @param longestRuns    single runs by their own hours, longest first (ties: lowest experience id); PENDIENTE
 *                       runs and runs with 0 hours are left out
 * @param shortGems      games rated 8 or more with 0 < total hours (runs + online) <= 15, best rating first
 *                       (ties: fewer hours, then name)
 * @param mostDaysPlayed games by distinct diary days, most days first (ties: name)
 * @param longestPending pending games as the library's "Pendiente" tab sees them (latest run PENDIENTE, or a
 *                       SINGLEPLAYER game with no runs), oldest waiting first (ties: lowest game id)
 */
public record GameRankingsDto(
        List<ReplayedGame> mostReplayed,
        List<LongRun> longestRuns,
        List<ShortGem> shortGems,
        List<DaysPlayedGame> mostDaysPlayed,
        List<PendingGame> longestPending) {

    /** @param hours sum of the counted runs' hours, 1 decimal */
    public record ReplayedGame(Long gameId, String gameName, long runCount, double hours) {
    }

    public record LongRun(Long experienceId, Long gameId, String gameName, String runLabel, double hours) {
    }

    /** @param rating the game rating (mean of its rated runs and online rating), 2 decimals; hours 1 decimal */
    public record ShortGem(Long gameId, String gameName, BigDecimal rating, double hours) {
    }

    public record DaysPlayedGame(Long gameId, String gameName, long days) {
    }

    /**
     * @param experienceId the pending run; null (with runLabel) for a game without runs
     * @param pendingSince when the pending run was created, or the game for a game without runs (estimated for
     *                     rows created before V39/V40)
     */
    public record PendingGame(Long gameId, String gameName, Long experienceId, String runLabel,
                              LocalDateTime pendingSince) {
    }
}
