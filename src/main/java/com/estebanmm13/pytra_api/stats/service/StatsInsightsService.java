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
}
