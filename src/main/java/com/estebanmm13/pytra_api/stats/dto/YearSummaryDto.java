package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

import java.util.List;

/**
 * Year in review.
 * <ul>
 *   <li>{@link #experiences} and {@link #experienceCount}: the runs played in the year (credits in it, or hours
 *       in it), each with its role and hours in the year.</li>
 *   <li>Completed, abandoned and the rating: the credits year (ExperiencePeriod.yearOf: explicit year, else
 *       endDate, else startDate).</li>
 *   <li>Platinums and 100%: the achievement year (postgame end, else credits).</li>
 *   <li>{@link #totalHours}, the months and {@link #hoursWithoutMonth}: the hours played in the year (RunHours:
 *       diary days, the rest spread from start to credits or postgame end), plus {@link #onlineHours}.</li>
 *   <li>The highlights: the attributions documented in YearHighlightsCalculator.</li>
 * </ul>
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
    private BigDecimal averageRating;
    private long platinumCount;
    private long fullCompletionCount;
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
    /** Diary hours of the year counted into online playtime; 0 without online diary lines. */
    private double onlineHours;
    /** Online games with diary hours in the year (their share of {@link #onlineHours}), most hours first. */
    private List<YearOnlineGameDto> onlineGames;
}
