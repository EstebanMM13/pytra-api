package com.estebanmm13.pytra_api.obsidian.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Full snapshot of a user's confirmed library for the Obsidian sync plugin (schema v{@value #SCHEMA_VERSION}).
 * The plugin detects deletions by comparing id sets, so every export is complete. Field names are a
 * public contract with the plugin: change them only together with {@code schemaVersion}.
 */
public record ObsidianExportDto(
        int schemaVersion,
        Instant serverTime,
        List<SagaDto> sagas,
        List<GameDto> games,
        List<ExperienceDto> experiences) {

    public static final int SCHEMA_VERSION = 1;

    public record SagaDto(Long id, String name, Instant updatedAt) {
    }

    /** {@code updatedAt} is the latest change of the game or of its online playtime. */
    public record GameDto(
            Long id,
            String name,
            Long sagaId,
            String category,
            List<String> genres,
            String developer,
            String publisher,
            LocalDate releaseDate,
            OnlineDto online,
            Instant updatedAt) {
    }

    public record OnlineDto(Double totalHours, List<String> platforms, BigDecimal rating) {
    }

    public record ExperienceDto(
            Long id,
            Long gameId,
            String runLabel,
            Integer year,
            String status,
            BigDecimal rating,
            Double hours,
            LocalDate startDate,
            LocalDate endDate,
            String platform,
            boolean platinum,
            boolean replay,
            Instant updatedAt) {
    }
}
