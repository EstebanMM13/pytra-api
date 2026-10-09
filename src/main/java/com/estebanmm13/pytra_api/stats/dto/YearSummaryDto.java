package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Year in review. Totals, months and {@link #experiences} use ExperiencePeriod (explicit year, else
 * endDate, else startDate); the highlights use the Obsidian attributions documented in
 * YearHighlightsCalculator ("completed in year" / "played in year").
 */
@Getter
@AllArgsConstructor
public class YearSummaryDto {
    private int year;
    private double totalHours;
    private long experienceCount;
    private long completedCount;
    private long abandonedCount;
    /** Mean rating of the year's rated experiences, 2 decimals; null when none is rated. */
    private Double averageRating;
    private long platinumCount;
    /** Hours of runs that belong to the year but have no date inside it, so no month bucket. */
    private double hoursWithoutMonth;
    /** Always 12 entries, January first; months without hours are 0. */
    private List<MonthHoursDto> months;
    /** Ordered by rating desc (unrated last), then hours desc. */
    private List<YearExperienceDto> experiences;

    /** Game with the best average over "completed in year" rated runs; null without data. */
    private RatedGameDto goty;
    private List<RatedGameDto> topRated;
    private List<MostPlayedGameDto> mostPlayed;
    private List<YearSagaStatDto> topSagas;
    private List<YearGenreStatDto> topGenres;
    private List<RatedGameBriefDto> surprises;
    private List<RatedGameBriefDto> disappointments;
    /** Never null: texts are null when the user has not written a note for the year. */
    private YearNoteDto note;
}
