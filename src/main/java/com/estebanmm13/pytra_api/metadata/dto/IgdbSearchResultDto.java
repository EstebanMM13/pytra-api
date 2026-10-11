package com.estebanmm13.pytra_api.metadata.dto;

import java.util.List;

/**
 * One hit of an IGDB search.
 *
 * @param year      year of the first release (UTC); null when IGDB has no date
 * @param imageUrl  small portrait cover ({@code t_cover_small}, https); null when IGDB has none
 * @param platforms short platform names (abbreviation when IGDB has one, e.g. "PS5", "Switch"), never null
 */
public record IgdbSearchResultDto(long igdbId, String name, Integer year, String imageUrl, List<String> platforms) {
}
