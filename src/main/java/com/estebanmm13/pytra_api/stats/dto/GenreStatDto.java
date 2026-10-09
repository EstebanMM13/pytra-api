package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GenreStatDto {
    private Long genreId;
    private String genreName;
    private long gameCount;
    private double totalHours;
}
