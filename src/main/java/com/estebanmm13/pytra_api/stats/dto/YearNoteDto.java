package com.estebanmm13.pytra_api.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/** Free-text yearly review. Both texts are null when the user never wrote one. */
@Getter
@AllArgsConstructor
public class YearNoteDto {
    private String summary;
    private String highlights;
    private LocalDateTime updatedAt;
}
