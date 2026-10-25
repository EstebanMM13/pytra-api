package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** An online game played in the year: its diary hours of that year counted into its online playtime. */
@Getter
@AllArgsConstructor
public class YearOnlineGameDto {
    private Long gameId;
    private String gameName;
    private String coverImageUrl;
    private double hours;
    /** Diary days of the year with hours counted for the game. */
    private long dayCount;
}
