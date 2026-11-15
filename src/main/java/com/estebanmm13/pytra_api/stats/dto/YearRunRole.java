package com.estebanmm13.pytra_api.stats.dto;

/**
 * What a run was in one year of the year page.
 * <ul>
 *   <li>{@code COMPLETED} / {@code ABANDONED}: its credits (or abandonment) fall in the year.</li>
 *   <li>{@code POSTGAME}: beaten in an earlier year, played after the credits in this one.</li>
 *   <li>{@code PLAYING}: played in the year before its credits, or still in progress.</li>
 * </ul>
 */
public enum YearRunRole {
    COMPLETED,
    ABANDONED,
    POSTGAME,
    PLAYING
}
