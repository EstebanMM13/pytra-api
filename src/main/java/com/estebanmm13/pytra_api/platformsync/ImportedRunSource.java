package com.estebanmm13.pytra_api.platformsync;

import com.estebanmm13.pytra_api.experiences.model.Experience;
import com.estebanmm13.pytra_api.experiences.model.ExperienceStatus;
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

    /** True when the run is played on this source's catalog platform. */
    public boolean isOnPlatform(Experience run) {
        return run.getPlatform() != null
                && run.getPlatform().getLegacyCode() != null
                && platformLegacyCode.equalsIgnoreCase(run.getPlatform().getLegacyCode());
    }

    /**
     * True when the run is this source's canonical run exactly as the sync created it: still in progress on
     * this source's platform, with the import label and note, and nothing the user typed (rating, year,
     * dates, platinum, replay, summary, pros, cons, other notes).
     */
    public boolean isUntouchedImportedRun(Experience run) {
        return runLabel.equals(run.getRunLabel())
                && run.getStatus() == ExperienceStatus.EN_CURSO
                && isOnPlatform(run)
                && (isBlank(run.getNotes()) || note.equals(run.getNotes()))
                && run.getRating() == null
                && run.getYear() == null
                && run.getStartDate() == null
                && run.getEndDate() == null
                && !Boolean.TRUE.equals(run.getPlatinum())
                && !Boolean.TRUE.equals(run.getReplay())
                && isBlank(run.getSummary())
                && isBlank(run.getPros())
                && isBlank(run.getCons());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
