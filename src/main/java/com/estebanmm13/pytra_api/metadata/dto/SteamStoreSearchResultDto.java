package com.estebanmm13.pytra_api.metadata.dto;

/**
 * One hit of a Steam store search.
 *
 * @param imageUrl small capsule image ({@code tiny_image}); null when Steam omits it
 */
public record SteamStoreSearchResultDto(long appId, String name, String imageUrl) {
}
