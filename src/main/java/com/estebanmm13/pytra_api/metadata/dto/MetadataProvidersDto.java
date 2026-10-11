package com.estebanmm13.pytra_api.metadata.dto;

/**
 * Which autofill sources this deployment offers. Steam's store needs no credentials; IGDB needs
 * the Twitch client id/secret.
 */
public record MetadataProvidersDto(boolean igdb, boolean steam) {
}
