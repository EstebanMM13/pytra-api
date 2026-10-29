package com.estebanmm13.pytra_api.diary.dto;

import java.math.BigDecimal;

/**
 * A game of a diary day. {@code countsHours}: the hours were added to the game's data (the run
 * {@code experienceId}, or the online playtime when it is null). {@code synced}: the game is linked to
 * Steam/PSN, whose sync counts the real playtime instead. {@code autoSynced}: the line was written by the
 * Steam/PSN sync (record only, it never counts hours).
 */
public record DiaryGameResponseDto(
        Long gameId,
        String gameName,
        String coverImageUrl,
        Long experienceId,
        String runLabel,
        BigDecimal hours,
        String note,
        boolean countsHours,
        boolean synced,
        boolean autoSynced
) {
}
