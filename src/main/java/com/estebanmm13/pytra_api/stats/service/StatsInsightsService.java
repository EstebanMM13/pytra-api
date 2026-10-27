package com.estebanmm13.pytra_api.stats.service;

import com.estebanmm13.pytra_api.stats.dto.*;

/**
 * Deeper stats breakdowns (activity, platforms, developers, release eras, completion habits, rating
 * profile). Every method is scoped to one user. An optional {@code year} uses the same year rule as the
 * other stats ({@link com.estebanmm13.pytra_api.experiences.model.ExperiencePeriod#yearOf}); null means
 * all-time unless a method says otherwise.
 */
public interface StatsInsightsService {

    /** Diary activity of {@code year} (current year when null) plus all-time streaks. */
    ActivityStatsDto getActivity(Long userId, Integer year);

    /** Per-platform hours, games, runs and ratings in scope, plus every year's hours per platform. */
    PlatformStatsDto getPlatforms(Long userId, Integer year);

    /**
     * All-time developers of the CONFIRMED games: top {@code limit} (1-50) by hours, and top {@code limit} by
     * average rating among those with at least {@code minRatedGames} (min 1) rated games.
     */
    DeveloperStatsDto getDevelopers(Long userId, int limit, int minRatedGames);

    /** All-time CONFIRMED games grouped by release decade and release year. */
    ReleaseEraStatsDto getReleaseEras(Long userId);

    /** Time to finish, abandonment per genre, hours versus rating and replays of the runs in scope. */
    CompletionStatsDto getCompletion(Long userId, Integer year);
}
