package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.games.model.ExternalPlatform;

/**
 * How hours imported from an external platform are labelled, and which catalog platform their
 * canonical run is played on.
 *
 * @param runLabel           label of the canonical Experience kept up to date by the sync
 * @param note               note stored on that Experience
 * @param platformLegacyCode seeded catalog entry ({@code platforms.legacy_code}) the run is played on
 */
public record ImportedRunSource(String runLabel, String note, String platformLegacyCode) {

    static final ImportedRunSource STEAM = new ImportedRunSource(
            "Importado de Steam", "Horas importadas automáticamente desde Steam", "PC");
    static final ImportedRunSource PSN_PS5 = new ImportedRunSource(
            "Importado de PlayStation", "Horas importadas automáticamente desde PlayStation Network", "PS5");
    static final ImportedRunSource PSN_PS4 = new ImportedRunSource(
            "Importado de PlayStation", "Horas importadas automáticamente desde PlayStation Network", "PS4");

    public static ImportedRunSource of(ExternalPlatform platform) {
        return switch (platform) {
            case STEAM -> STEAM;
            case PSN_PS5 -> PSN_PS5;
            case PSN_PS4 -> PSN_PS4;
            default -> throw new IllegalArgumentException("No import source for platform " + platform);
        };
    }
}
