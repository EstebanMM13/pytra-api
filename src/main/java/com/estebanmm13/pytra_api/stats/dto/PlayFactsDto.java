package com.estebanmm13.pytra_api.stats.dto;

import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;

import java.util.List;

/**
 * Compact play facts of every run and every year of counted online diary hours, so a client can filter the
 * library by the same rules as the stats breakdowns (played year, platform within a year, days to finish,
 * run length) without asking the server per filter.
 *
 * @param runs        every run of the user (pending imports included, like the run stats), by id
 * @param onlineYears one entry per game and year with counted online diary hours above 0, by game then year
 */
public record PlayFactsDto(List<RunFact> runs, List<OnlineYear> onlineYears) {

    /**
     * @param year       ExperiencePeriod#yearOf; null when the run has no year nor dates
     * @param hours      run hours (0 when unset), 2 decimals
     * @param daysToFinish calendar days from start to end of a COMPLETADO run with both dates (end not before
     *                   start), as the completion stats count them; null otherwise
     */
    public record RunFact(Long experienceId, Long gameId, Long platformId, Integer year, ExperienceStatus status,
                          double hours, Long daysToFinish) {
    }

    /** {@code platformIds}: the platforms of the game's online playtime (empty when none), in catalog order. */
    public record OnlineYear(Long gameId, int year, List<Long> platformIds) {
    }
}
