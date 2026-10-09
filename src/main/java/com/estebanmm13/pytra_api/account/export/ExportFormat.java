package com.estebanmm13.pytra_api.account.export;

import com.estebanmm13.pytra_api.error.InvalidRequestException;
import org.springframework.http.MediaType;

import java.util.Locale;

public enum ExportFormat {
    CSV("csv", new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8)),
    MARKDOWN("md", new MediaType("text", "markdown", java.nio.charset.StandardCharsets.UTF_8));

    private final String extension;
    private final MediaType mediaType;

    ExportFormat(String extension, MediaType mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    /** Accepts {@code csv}, {@code markdown} or {@code md}, case-insensitive. */
    public static ExportFormat fromParam(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "csv" -> CSV;
            case "markdown", "md" -> MARKDOWN;
            default -> throw new InvalidRequestException(InvalidRequestException.INVALID_EXPORT_FORMAT);
        };
    }
}
