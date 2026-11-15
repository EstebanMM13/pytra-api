package com.estebanmm13.pytra_api.diary.dto;

import java.util.List;

/**
 * A game linked to a playtime sync. {@code sources}: its syncs ({@code STEAM}, {@code PSN}, {@code ANDROID}).
 * {@code covered}: the catalog platforms whose hours those syncs already count, each with the sync that counts
 * them; a diary line on one of them is only recorded, a line on any other platform adds its hours.
 */
public record DiaryLinkedGameDto(Long gameId, List<String> sources, List<CoveredPlatform> covered) {

    public record CoveredPlatform(Long platformId, String source) {
    }
}
